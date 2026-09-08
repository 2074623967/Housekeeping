package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentRouteContextDTO;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 支付路由规则表达式求值器。
 */
final class PaymentRouteExpressionEvaluator {

    private PaymentRouteExpressionEvaluator() {
    }

    static boolean matches(PaymentRouteContextDTO context, String expression) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        try {
            Parser parser = new Parser(context, tokenize(expression));
            boolean result = parser.parseExpression();
            parser.require(TokenType.EOF);
            return result;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isValid(String expression) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        try {
            Parser parser = new Parser(new PaymentRouteContextDTO(), tokenize(expression));
            parser.parseExpression();
            parser.require(TokenType.EOF);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static List<Token> tokenize(String expression) {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '(') {
                tokens.add(new Token(TokenType.LEFT_PARENTHESIS, "("));
                index++;
                continue;
            }
            if (current == ')') {
                tokens.add(new Token(TokenType.RIGHT_PARENTHESIS, ")"));
                index++;
                continue;
            }
            if (current == ',') {
                tokens.add(new Token(TokenType.COMMA, ","));
                index++;
                continue;
            }
            if (current == '\'' || current == '"') {
                int closing = expression.indexOf(current, index + 1);
                if (closing < 0) {
                    throw new IllegalArgumentException("unterminated quoted value");
                }
                tokens.add(new Token(TokenType.VALUE, expression.substring(index + 1, closing)));
                index = closing + 1;
                continue;
            }
            if (current == '!' || current == '>' || current == '<' || current == '=') {
                int next = index + 1;
                boolean hasEquals = next < expression.length() && expression.charAt(next) == '=';
                String operator = expression.substring(index, index + (hasEquals ? 2 : 1));
                tokens.add(new Token(operatorType(operator), operator));
                index += operator.length();
                continue;
            }
            int end = index;
            while (end < expression.length() && !isDelimiter(expression.charAt(end))) {
                end++;
            }
            String value = expression.substring(index, end);
            tokens.add(new Token(keywordType(value), value));
            index = end;
        }
        tokens.add(new Token(TokenType.EOF, ""));
        return tokens;
    }

    private static boolean isDelimiter(char value) {
        return Character.isWhitespace(value)
                || value == '(' || value == ')' || value == ','
                || value == '!' || value == '>' || value == '<' || value == '=';
    }

    private static TokenType operatorType(String operator) {
        switch (operator) {
            case "=":
                return TokenType.EQUAL;
            case "!=":
                return TokenType.NOT_EQUAL;
            case ">":
                return TokenType.GREATER_THAN;
            case ">=":
                return TokenType.GREATER_THAN_OR_EQUAL;
            case "<":
                return TokenType.LESS_THAN;
            case "<=":
                return TokenType.LESS_THAN_OR_EQUAL;
            default:
                throw new IllegalArgumentException("unsupported operator");
        }
    }

    private static TokenType keywordType(String value) {
        if ("AND".equalsIgnoreCase(value)) {
            return TokenType.AND;
        }
        if ("OR".equalsIgnoreCase(value)) {
            return TokenType.OR;
        }
        if ("NOT".equalsIgnoreCase(value)) {
            return TokenType.NOT;
        }
        if ("IN".equalsIgnoreCase(value)) {
            return TokenType.IN;
        }
        return TokenType.VALUE;
    }

    private enum TokenType {
        VALUE,
        EQUAL,
        NOT_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        AND,
        OR,
        NOT,
        IN,
        LEFT_PARENTHESIS,
        RIGHT_PARENTHESIS,
        COMMA,
        EOF
    }

    private static final class Token {
        private final TokenType type;
        private final String value;

        private Token(TokenType type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    private static final class Parser {
        private final PaymentRouteContextDTO context;
        private final List<Token> tokens;
        private int position;

        private Parser(PaymentRouteContextDTO context, List<Token> tokens) {
            this.context = context;
            this.tokens = tokens;
        }

        private boolean parseExpression() {
            return parseOr();
        }

        private boolean parseOr() {
            boolean result = parseAnd();
            while (match(TokenType.OR)) {
                boolean right = parseAnd();
                result = result || right;
            }
            return result;
        }

        private boolean parseAnd() {
            boolean result = parseUnary();
            while (match(TokenType.AND)) {
                boolean right = parseUnary();
                result = result && right;
            }
            return result;
        }

        private boolean parseUnary() {
            if (match(TokenType.NOT)) {
                return !parseUnary();
            }
            if (match(TokenType.LEFT_PARENTHESIS)) {
                boolean result = parseExpression();
                require(TokenType.RIGHT_PARENTHESIS);
                return result;
            }
            return parseComparison();
        }

        private boolean parseComparison() {
            String field = require(TokenType.VALUE).value;
            if (match(TokenType.IN)) {
                return parseIn(field);
            }
            Token operator = consumeComparisonOperator();
            String expectedValue = require(TokenType.VALUE).value;
            return compare(field, operator.type, expectedValue);
        }

        private boolean parseIn(String field) {
            require(TokenType.LEFT_PARENTHESIS);
            boolean result = compare(field, TokenType.EQUAL, require(TokenType.VALUE).value);
            while (match(TokenType.COMMA)) {
                boolean matchedValue = compare(field, TokenType.EQUAL, require(TokenType.VALUE).value);
                result = result || matchedValue;
            }
            require(TokenType.RIGHT_PARENTHESIS);
            return result;
        }

        private Token consumeComparisonOperator() {
            Token token = peek();
            if (token.type == TokenType.EQUAL
                    || token.type == TokenType.NOT_EQUAL
                    || token.type == TokenType.GREATER_THAN
                    || token.type == TokenType.GREATER_THAN_OR_EQUAL
                    || token.type == TokenType.LESS_THAN
                    || token.type == TokenType.LESS_THAN_OR_EQUAL) {
                position++;
                return token;
            }
            throw new IllegalArgumentException("comparison operator required");
        }

        private boolean compare(String field, TokenType operator, String expectedValue) {
            String normalizedField = normalize(field);
            if ("amount".equals(normalizedField)) {
                return compareAmount(operator, expectedValue);
            }
            if (operator != TokenType.EQUAL && operator != TokenType.NOT_EQUAL) {
                throw new IllegalArgumentException("string field only supports equality comparison");
            }
            String actualValue = resolveTextValue(normalizedField);
            String normalizedExpectedValue = normalizeExpectedValue(normalizedField, expectedValue);
            boolean equals = actualValue.equals(normalizedExpectedValue);
            return operator == TokenType.EQUAL ? equals : !equals;
        }

        private boolean compareAmount(TokenType operator, String expectedValue) {
            BigDecimal actualAmount = context.getAmount() == null ? BigDecimal.ZERO : context.getAmount();
            int result = actualAmount.compareTo(new BigDecimal(expectedValue.trim()));
            switch (operator) {
                case EQUAL:
                    return result == 0;
                case NOT_EQUAL:
                    return result != 0;
                case GREATER_THAN:
                    return result > 0;
                case GREATER_THAN_OR_EQUAL:
                    return result >= 0;
                case LESS_THAN:
                    return result < 0;
                case LESS_THAN_OR_EQUAL:
                    return result <= 0;
                default:
                    throw new IllegalArgumentException("unsupported amount operator");
            }
        }

        private String resolveTextValue(String field) {
            switch (field) {
                case "paymentmethod":
                    return normalize(context.getPaymentMethod());
                case "customertype":
                    return resolveCustomerType(context.getCustomerName());
                case "requestedchannel":
                case "channelcode":
                    return normalizeChannelCode(context.getRequestedChannelCode());
                case "terminal":
                    return normalizeTerminal(context.getTerminal());
                case "payscene":
                    return normalize(context.getPayScene());
                default:
                    throw new IllegalArgumentException("unsupported route field");
            }
        }

        private String normalizeExpectedValue(String field, String value) {
            if ("requestedchannel".equals(field) || "channelcode".equals(field)) {
                return normalizeChannelCode(value);
            }
            if ("terminal".equals(field)) {
                return normalizeTerminal(value);
            }
            return normalize(value);
        }

        private boolean match(TokenType type) {
            if (peek().type != type) {
                return false;
            }
            position++;
            return true;
        }

        private Token require(TokenType type) {
            Token token = peek();
            if (token.type != type) {
                throw new IllegalArgumentException("unexpected token");
            }
            position++;
            return token;
        }

        private Token peek() {
            return tokens.get(position);
        }
    }

    private static String resolveCustomerType(String customerName) {
        return normalize(customerName).contains("企业") ? "enterprise" : "personal";
    }

    private static String normalizeTerminal(String terminal) {
        String normalizedTerminal = normalize(terminal);
        if ("mobile_web".equals(normalizedTerminal)
                || "mobile".equals(normalizedTerminal)
                || "h5".equals(normalizedTerminal)) {
            return "h5";
        }
        if ("pc_web".equals(normalizedTerminal)
                || "pc".equals(normalizedTerminal)
                || "web".equals(normalizedTerminal)) {
            return "pc";
        }
        if ("app".equals(normalizedTerminal) || "app_native".equals(normalizedTerminal)) {
            return "app";
        }
        return normalizedTerminal;
    }

    private static String normalizeChannelCode(String channelCode) {
        String normalizedChannel = normalize(channelCode);
        if (!StringUtils.hasText(normalizedChannel)) {
            return "";
        }
        if (normalizedChannel.contains("wx") || normalizedChannel.contains("wechat")) {
            return "wx_h5";
        }
        if (normalizedChannel.contains("ali")) {
            return "alipay_h5";
        }
        if (normalizedChannel.contains("bank")) {
            return "offline_bank";
        }
        return normalizedChannel;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

package com.abc123.hsp.dto;

import java.util.List;

public class DashboardSummaryDTO {

    private List<DashboardCardDTO> cards;

    public DashboardSummaryDTO() {
    }

    public DashboardSummaryDTO(List<DashboardCardDTO> cards) {
        this.cards = cards;
    }

    public List<DashboardCardDTO> getCards() {
        return cards;
    }

    public void setCards(List<DashboardCardDTO> cards) {
        this.cards = cards;
    }
}

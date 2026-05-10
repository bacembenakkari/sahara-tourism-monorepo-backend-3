package com.camping.duneinsolite.dto.statistics;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SourceStatsDTO {

    private List<SourceEntryDTO> sources;
    private Long                 totalReservations;

    @Data
    @Builder
    public static class SourceEntryDTO {
        private String source;
        private Long   count;
        private Double percentage;
    }
}
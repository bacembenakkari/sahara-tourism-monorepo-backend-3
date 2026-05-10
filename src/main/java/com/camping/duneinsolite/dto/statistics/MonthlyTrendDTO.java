package com.camping.duneinsolite.dto.statistics;


import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MonthlyTrendDTO {

    private List<String>  labels;
    private List<Double>  revenue;
    private List<Long>    reservations;
    private Integer       year;
}

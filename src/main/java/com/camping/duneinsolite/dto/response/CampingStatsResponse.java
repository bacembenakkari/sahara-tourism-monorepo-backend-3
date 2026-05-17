package com.camping.duneinsolite.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CampingStatsResponse {

    // Stat 1 — currently CHECKED_IN
    private int inCampAdults;
    private int inCampChildren;
    private int inCampTotal;

    // Stat 2 — arriving today (CONFIRMED + date = today)
    private int arrivingTodayAdults;
    private int arrivingTodayChildren;
    private int arrivingTodayTotal;
}

package com.camping.duneinsolite.mapper;

import com.camping.duneinsolite.dto.response.TourHebergementRepartitionResponse;
import com.camping.duneinsolite.dto.response.TourHebergementResponse;
import com.camping.duneinsolite.model.ReservationTourHebergement;
import com.camping.duneinsolite.model.ReservationTourHebergementRepartition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReservationTourHebergementMapper {

    TourHebergementResponse toResponse(ReservationTourHebergement hebergement);

    @Mapping(target = "capacityPerTente", expression = "java(r.getTenteType() != null ? r.getTenteType().getCapacity() : null)")
    @Mapping(target = "totalPersonnes", expression = "java(r.getTotalPersonnes())")
    TourHebergementRepartitionResponse toRepartitionResponse(ReservationTourHebergementRepartition r);
}

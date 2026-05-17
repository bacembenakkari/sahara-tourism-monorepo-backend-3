package com.camping.duneinsolite.mapper;

import com.camping.duneinsolite.dto.response.ParticipantResponse;
import com.camping.duneinsolite.dto.response.RepartitionResponse;
import com.camping.duneinsolite.dto.response.ReservationResponse;
import com.camping.duneinsolite.model.Participant;
import com.camping.duneinsolite.model.Reservation;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring", uses = {
        ReservationTourTypeMapper.class,
        ReservationExtraMapper.class,
        ReservationTourMapper.class,
        SourceMapper.class,
        GuideMapper.class,
        ChauffeurMapper.class
})
public interface ReservationMapper {

    @Mapping(source = "user.userId",   target = "userId")
    @Mapping(source = "user.name",     target = "userName")
    @Mapping(source = "sourceRef",     target = "source")
    @Mapping(source = "guides",        target = "guides")
    @Mapping(source = "chauffeurs",    target = "chauffeurs")
    ReservationResponse toResponse(Reservation reservation);

    ParticipantResponse toParticipantResponse(Participant participant);

    @AfterMapping
    default void mapRepartitions(Reservation source, @MappingTarget ReservationResponse target) {
        if (source.getRepartitions() == null) return;
        List<RepartitionResponse> mapped = source.getRepartitions().stream().map(r -> {
            RepartitionResponse resp = new RepartitionResponse();
            resp.setRepartitionId(r.getRepartitionId());
            resp.setTenteType(r.getTenteType());
            resp.setNumberOfTentes(r.getNumberOfTentes());
            resp.setCapacityPerTente(r.getTenteType() != null ? r.getTenteType().getCapacity() : 0);
            resp.setTotalPersonnes(r.getTotalPersonnes());
            return resp;
        }).toList();
        target.setRepartitions(mapped);
    }
}
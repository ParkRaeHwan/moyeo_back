package com.example.capstone.plan.repository;

import com.example.capstone.plan.entity.TravelPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<TravelPlace, Long> {

    // 특정 일정의 모든 place 조회 (정렬 포함)
    List<TravelPlace> findAllByTravelDayIdInOrderByTravelDayIdAscPlaceOrderAsc(List<Long> dayIds);

    // 단일 Day에 속한 place 전부 삭제 (resave용)
    @Modifying
    @Query("DELETE FROM TravelPlace p WHERE p.travelDay.id = :dayId")
    void deleteAllByTravelDayId(@Param("dayId") Long dayId);
}

package com.example.capstone.plan.repository;

import com.example.capstone.plan.entity.TravelDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DayRepository extends JpaRepository<TravelDay, Long> {

    List<TravelDay> findAllByTravelScheduleId(Long scheduleId);

}

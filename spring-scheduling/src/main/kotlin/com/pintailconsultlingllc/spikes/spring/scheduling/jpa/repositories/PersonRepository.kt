package com.pintailconsultlingllc.spikes.spring.scheduling.jpa.repositories

import com.pintailconsultlingllc.spikes.spring.scheduling.jpa.entities.Person
import org.springframework.data.jpa.repository.JpaRepository

interface PersonRepository : JpaRepository<Person, Int>



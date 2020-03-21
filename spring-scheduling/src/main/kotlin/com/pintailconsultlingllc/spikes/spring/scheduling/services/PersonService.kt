package com.pintailconsultlingllc.spikes.spring.scheduling.services

import com.pintailconsultlingllc.spikes.spring.scheduling.jpa.entities.Person
import com.pintailconsultlingllc.spikes.spring.scheduling.jpa.repositories.PersonRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class PersonService(val personRepository: PersonRepository) {

    @Async
    @Scheduled(fixedRate = 5000)
    fun doCount() {
        val count = personRepository.count()
        logger.info { "There are currently $count persons in the database" }
        Thread.sleep(10000L)
    }

    @Async
    @Scheduled(fixedRate = 4000)
    fun doInsert() {
        val currentTimeMillis = System.currentTimeMillis()
        val person = Person(firstName = "Christopher-$currentTimeMillis", lastName = "Bartling-$currentTimeMillis")
        val savedPerson = personRepository.save(person)
        logger.info { "Inserted a new person into the database: $savedPerson" }
        Thread.sleep(5000L)
    }
}

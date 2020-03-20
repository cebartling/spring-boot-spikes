package com.pintailconsultlingllc.spikes.spring.scheduling.services

import com.pintailconsultlingllc.spikes.spring.scheduling.jpa.entities.Person
import com.pintailconsultlingllc.spikes.spring.scheduling.jpa.repositories.PersonRepository
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class PersonService(val personRepository: PersonRepository) {

    @Scheduled(fixedRate = 5000)
    fun doCount() {
        val count = personRepository.count()
        logger.info { "There are currently $count persons in the database" }
    }

    @Scheduled(fixedRate = 4000)
    fun doInsert() {
        val person = Person(firstName = "Christopher", lastName = "Bartling")
        val savedPerson = personRepository.save(person)
        logger.info { "Inserted a new person into the database: $savedPerson" }
    }
}

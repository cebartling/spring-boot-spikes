package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.InventoryLocation
import com.pintailconsultingllc.resiliencyspike.repository.InventoryLocationRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service for managing inventory locations
 */
@Service
class InventoryLocationService(
    private val inventoryLocationRepository: InventoryLocationRepository
) {
    fun findLocationById(id: UUID): Mono<InventoryLocation> =
        inventoryLocationRepository.findById(id)

    fun findLocationByCode(code: String): Mono<InventoryLocation> =
        inventoryLocationRepository.findByCode(code)

    fun findAllLocations(): Flux<InventoryLocation> =
        inventoryLocationRepository.findAll()

    fun findActiveLocations(): Flux<InventoryLocation> =
        inventoryLocationRepository.findByIsActive(true)

    fun findLocationsByType(type: String): Flux<InventoryLocation> =
        inventoryLocationRepository.findByType(type)

    fun findActiveLocationsByType(type: String): Flux<InventoryLocation> =
        inventoryLocationRepository.findByTypeAndIsActive(type, true)

    fun searchLocationsByName(searchTerm: String): Flux<InventoryLocation> =
        inventoryLocationRepository.searchByName(searchTerm)

    fun createLocation(location: InventoryLocation): Mono<InventoryLocation> =
        inventoryLocationRepository.save(location)

    fun updateLocation(location: InventoryLocation): Mono<InventoryLocation> =
        inventoryLocationRepository.save(location)

    fun deactivateLocation(locationId: UUID): Mono<InventoryLocation> =
        inventoryLocationRepository.findById(locationId)
            .flatMap { location ->
                inventoryLocationRepository.save(location.copy(isActive = false))
            }

    fun activateLocation(locationId: UUID): Mono<InventoryLocation> =
        inventoryLocationRepository.findById(locationId)
            .flatMap { location ->
                inventoryLocationRepository.save(location.copy(isActive = true))
            }

    fun deleteLocation(id: UUID): Mono<Void> =
        inventoryLocationRepository.deleteById(id)

    fun countLocationsByType(type: String): Mono<Long> =
        inventoryLocationRepository.countByType(type)
}

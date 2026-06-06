package dev.vinciguerra.adrsentinel.web.dto.vehicle;

import jakarta.validation.constraints.NotNull;

/**
 * Data Transfer Object (DTO) immutabile utilizzato per veicolare la richiesta di modifica 
 * dello stato operativo di un veicolo (Pattern Architetturale di Soft Delete / Riattivazione).
 * <p>
 * <b>Design Architetturale (Immutabilità e Sicurezza):</b><br>
 * L'utilizzo di un {@code record} nativo Java garantisce che il payload, una volta 
 * deserializzato dal Presentation Layer (es. il Controller REST), sia intrinsecamente 
 * read-only e thread-safe. Questo previene qualsiasi alterazione accidentale dei dati 
 * durante l'attraversamento dei layer applicativi verso il Service.
 * </p>
 * <p>
 * <b>Validazione Sintattica (Fail-Fast Boundary):</b><br>
 * La presenza dell'annotazione {@code @NotNull} erige uno scudo protettivo (Boundary Guard) 
 * direttamente all'ingresso dell'API. Delega al framework (es. Spring Validation) il compito 
 * di intercettare payload JSON malformati (ad esempio richieste prive del campo {@code "active"}) 
 * e di respingerle istantaneamente con un errore HTTP 400 (Bad Request), proteggendo la logica 
 * di business sottostante da insidiose {@code NullPointerException}.
 * </p>
 * @param active Il nuovo stato operativo desiderato per il veicolo. 
 * <ul>
 * <li>{@code true}: Il veicolo viene reso (o confermato) pienamente operativo 
 * e visibile per l'assegnazione a nuove spedizioni ({@code PLANNED}).</li>
 * <li>{@code false}: Il veicolo subisce una dismissione logica (Soft Delete), 
 * scomparendo dai flussi operativi futuri pur mantenendo intatta 
 * la propria integrità relazionale per l'Audit Storico.</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record VehicleUpdateActiveStatusDTO(@NotNull(message = "Malformed payload: active status is required") Boolean active) {}

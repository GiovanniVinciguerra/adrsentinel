package dev.vinciguerra.adrsentinel.web.dto.shipment;

import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorShipmentReason;

/**
 * Data Transfer Object (DTO) immutabile dedicato all'aggiornamento parziale della causale di trasporto.
 * <p>
 * Questo {@code record} è stato progettato specificamente per incapsulare il payload in ingresso
 * degli endpoint REST (tipicamente operazioni di tipo {@code PATCH}) deputati alla modifica 
 * isolata della ragione di trasporto per una spedizione ADR già consolidata a sistema.
 * </p>
 * <p>
 * <b>Vantaggi Architetturali:</b><br>
 * L'utilizzo del costrutto {@code record} nativo di Java garantisce l'assoluta immutabilità del dato
 * una volta deserializzato dal framework (es. Jackson). Questo elimina la necessità di boilerplate 
 * code (getter, setter, costruttori espliciti), ottimizzando l'impronta in memoria e garantendo un 
 * passaggio sicuro dei dati dallo strato Controller verso i Service.
 * </p>
 * <p>
 * <b>Sicurezza e Validazione (Fail-Fast):</b><br>
 * L'iniezione dell'annotazione {@link ValidatorShipmentReason} direttamente nel costruttore compatto
 * del record assicura in modo dichiarativo che il framework di validazione di Spring respinga
 * immediatamente la richiesta (restituendo tipicamente un HTTP 400 Bad Request) qualora la 
 * stringa fornita dal client sia nulla, formattata male o non coincidente con il dizionario ADR consentito.
 * </p>
 * @param shipmentReason la stringa JSON in ingresso rappresentante la nuova causale logistica,
 * rigorosamente vincolata al superamento della validazione di dominio
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentUpdateReasonDTO(@ValidatorShipmentReason String shipmentReason) {}

package dev.vinciguerra.adrsentinel.web.dto.shipment;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDateTime;

/**
 * Data Transfer Object (DTO) immutabile utilizzato per trasportare la richiesta 
 * di aggiornamento della data di una spedizione.
 * <p>
 * Questo record viene tipicamente impiegato come payload nei controller REST 
 * o nei servizi di ingresso, incapsulando la nuova data sotto forma di stringa 
 * prima che venga validata e convertita in un oggetto temporale di tipo 
 * {@link java.time.LocalDateTime}.
 * </p>
 * @param date la stringa che rappresenta la nuova data della spedizione. 
 * Questo parametro è sottoposto a validazione automatica tramite 
 * l'annotazione {@link ValidatorLocalDateTime} per garantire che sia 
 * un formato data/ora valido (es. {@code ISO-8601}) e conforme alle 
 * regole di business prima dell'elaborazione.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentUpdateDateDTO(@ValidatorLocalDateTime String date) {}

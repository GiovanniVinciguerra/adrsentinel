package dev.vinciguerra.adrsentinel.web.dto.shipment;

import java.util.List;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorAddress;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorLocalDateTime;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) progettato per la mutazione 
 * parziale (Partial Update / Rerouting) dei dettagli operativi di una spedizione ADR.
 * <p><b>Contesto Architetturale e Logica di Business:</b></p>
 * A differenza del payload di creazione completo, questo record modella specifiche 
 * operazioni di "Riprogrammazione Logistica". Viene tipicamente consumato quando:
 * <ul>
 * <li>Il cliente richiede un cambio di destinazione (Rerouting) in corso d'opera.</li>
 * <li>Si rende necessario posticipare o anticipare il marcatore temporale della partenza.</li>
 * <li>Avviene una sostituzione del mezzo (es. per guasto tecnico o indisponibilità), 
 * richiedendo l'assegnazione di una nuova targa.</li>
 * </ul>
 * <p><b>Edge Validation e Sicurezza Perimetrale:</b></p>
 * Come per il DTO di creazione, questo oggetto funge da scudo (Anti-Corruption Layer). 
 * Sfruttando i validatori custom ({@code @Validator...}), garantisce che ogni tentativo di 
 * aggiornamento contenga dati formalmente perfetti, sanitizzati contro iniezioni XSS 
 * e allineati agli standard internazionali (ISO 8601 per le date, formati europei per le targhe), 
 * bloccando le richieste malevole o errate prima che inneschino transazioni sul database.
 * <p><b>Scelta Architetturale (Java Record):</b></p>
 * L'uso del {@code record} garantisce che i dati di aggiornamento viaggino dal Controller 
 * al Service in uno stato di totale immutabilità e Thread-Safety.
 * @param date Il nuovo marcatore temporale per la riprogrammazione della spedizione.
 * (Nota tecnica: assicurarsi che il tipo di dato supporti la validazione tramite espressioni regolari).
 * @param destinations I nuovi indirizzi toponomastici di consegna (sanificati contro i metacaratteri).
 * @param vehicleLicensePlate La targa alfanumerica del nuovo veicolo assegnato al trasporto ADR.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentUpdateDTO(@ValidatorLocalDateTime String date, 
	@NotEmpty(message = "Malformed payload: Destinations list is required") List<@ValidatorAddress String> destinations, @ValidatorLicensePlate String vehicleLicensePlate) {}

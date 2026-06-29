package dev.vinciguerra.adrsentinel.web.dto.dispatch;

import java.util.List;
import dev.vinciguerra.adrsentinel.web.dto.driver.DriverResponseDTO;

/**
 * Data Transfer Object (DTO) immutabile che incapsula la risposta del motore di assegnazione 
 * autisti (Driver Dispatcher) da restituire al client chiamante.
 * <p>
 * Questa classe rappresenta l'esito positivo del processo di matchmaking tra i requisiti 
 * normativi del viaggio (es. durata, massa del veicolo, classi ADR) e la flotta aziendale. 
 * Viene generata esclusivamente dopo che il sistema ha verificato con successo la disponibilità 
 * e la conformità legale (CQC, patentini ADR, scadenze) del personale viaggiante.
 * </p>
 * <p>
 * <b>Scelte Architetturali:</b>
 * <ul>
 * <li><b>Multi-Manning (Doppia Guida):</b> La scelta di incapsulare il risultato in una {@code List} 
 * non è casuale. Rispecchia la necessità logistica e normativa (Regolamento CE 561/2006) di 
 * supportare l'equipaggio multiplo. Per viaggi standard la lista conterrà un singolo elemento, 
 * mentre per tratte stimate oltre le 10 ore conterrà due autisti qualificati.</li>
 * <li><b>Immutabilità e Sicurezza:</b> L'utilizzo del costrutto {@code record} garantisce 
 * l'immutabilità nativa e thread-safe del payload. Inoltre, restituendo un {@link DriverResponseDTO} 
 * anziché l'entità JPA {@code Driver}, si implementa una rigorosa separazione dei ruoli 
 * (Separation of Concerns), prevenendo l'esposizione accidentale di dati sensibili del database 
 * o il trigger di eccezioni come la {@code LazyInitializationException} durante la serializzazione JSON.</li>
 * </ul>
 * </p>
 * @param dispatches La lista contenente le anagrafiche filtrate e formattate degli autisti 
 * assegnati al viaggio. L'ordine della lista riflette la priorità di assegnazione 
 * (es. ottimizzazione basata sul minor numero di specializzazioni sprecate).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 * @see DriverResponseDTO
 */
public record DriverDispatchResponseDTO(List<DriverResponseDTO> dispatches) {}

package dev.vinciguerra.adrsentinel.web.dto.adrclass;

/**
 * Data Transfer Object (DTO) in uscita per la rappresentazione in sola lettura di una Classe ADR.
 * <p>
 * <b>Ruolo Architetturale (Data Hiding & Payload Optimization):</b><br>
 * Questo record funge da "vetrina" o "passaporto" per l'esposizione dei dati verso il client 
 * (es. un frontend React). Garantisce che l'entità di database originale, con le sue annotazioni JPA 
 * e i potenziali metadati tecnici, non attraversi mai il confine della rete (Boundary Layer). 
 * Ottimizza inoltre le prestazioni dell'API, serializzando in JSON esclusivamente i campi 
 * strettamente necessari per il rendering dell'interfaccia utente.
 * </p>
 * <p>
 * <b>Immutabilità e Sicurezza (Java Record):</b><br>
 * Essendo implementato come {@code record} nativo, questo oggetto è strutturalmente <b>Immutabile</b> 
 * e <b>Thread-Safe</b>. Una volta "forgiato" dal Mapper del Controller, il suo stato è sigillato. 
 * Questo previene qualsiasi alterazione accidentale dei dati durante i processi asincroni o durante 
 * la serializzazione eseguita dalla libreria Jackson.
 * </p>
 * @param id l'identificatore tecnico univoco (Surrogate Key) assegnato dal database. 
 * È un metadato vitale per il framework frontend: permette a React di gestire 
 * in modo efficiente il Virtual DOM (tramite la prop {@code key} nelle liste) 
 * e funge da riferimento per eventuali chiamate relazionali.
 * @param classCode il codice alfanumerico ufficiale della normativa ADR (Business Key) 
 * (es. "3", "4.1", "1.4S"). Rappresenta l'identificatore logistico primario.
 * @param description la descrizione estesa e leggibile (Human-Readable) della natura del pericolo 
 * (es. "Liquidi infiammabili"). Questo campo è pre-formattato e pronto 
 * per essere inserito direttamente nei menu a tendina o nelle tabelle operative della UI.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Read-Only Immutable View)
 * @since 1.0
 */
public record AdrClassResponseDTO(Long id, String classCode, String description) {}

package dev.vinciguerra.adrsentinel.web.dto.adrclass;

import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrClassCode;
import dev.vinciguerra.adrsentinel.web.annotation.adrclass.ValidatorAdrDescription;

/**
 * Data Transfer Object (DTO) in ingresso, dedicato alla deserializzazione e validazione 
 * dei payload JSON per le operazioni di Creazione (POST) e Aggiornamento (PUT) di una Classe ADR.
 * <p>
 * <b>Ruolo Architetturale (Boundary Hiding & Fail-Fast):</b><br>
 * Questo record rappresenta la "dogana" in ingresso dell'applicazione. Il suo scopo esclusivo 
 * è intercettare i dati grezzi provenienti dal client Web e sottoporli a uno scrutinio rigoroso 
 * prima che possano raggiungere il cuore del sistema (Service Layer). Utilizza il paradigma di 
 * validazione dichiarativa (JSR 380) per respingere istantaneamente, tramite un HTTP 400 Bad Request, 
 * qualsiasi input incompleto o malformato, proteggendo l'integrità del database.
 * </p>
 * <p>
 * <b>Design Pattern (Clean Code & Constraint Composition):</b><br>
 * L'estrema sintesi visiva di questa classe è il risultato di una scelta architetturale precisa. 
 * L'assenza di annotazioni standard (come {@code @NotBlank} o {@code @Size}) è intenzionale: 
 * l'intera complessità delle regole di validazione è stata astratta e incapsulata all'interno 
 * di annotazioni custom ({@link ValidatorAdrClassCode} e {@link ValidatorAdrClassDescription}). 
 * Questo approccio garantisce la totale aderenza al principio DRY (Don't Repeat Yourself) e 
 * rende il DTO un manifesto dichiarativo di altissima leggibilità.
 * </p>
 * <p>
 * <b>Immutabilità Nativa (Java Record):</b><br>
 * Essendo implementato come {@code record}, questo contenitore di dati è strutturalmente 
 * <b>Immutabile</b> e <b>Thread-Safe</b>. Una volta instanziato e popolato dalla libreria Jackson 
 * durante la deserializzazione della richiesta HTTP, il suo stato viene sigillato, prevenendo 
 * qualsiasi alterazione collaterale durante i passaggi tra i vari layer applicativi.
 * </p>
 * @param classCode il codice identificativo della classe ADR fornito dal client (Business Key). 
 * È blindato dall'annotazione {@link ValidatorAdrClassCode}, la quale garantisce internamente 
 * che il valore non sia vuoto o composto da soli spazi, e che rispetti rigorosamente l'espressione 
 * regolare definita dalla normativa ADR (es. "3", "4.1", "1.4S").
 * @param description la descrizione formale e testuale del pericolo associato alla classe. 
 * È protetta dall'annotazione {@link ValidatorAdrClassDescription}, che ne garantisce la presenza 
 * obbligatoria e previene errori di troncamento SQL (Data Truncation Exception) bloccando 
 * stringhe superiori alla lunghezza massima consentita dalle colonne del database.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record AdrClassRequestDTO(@ValidatorAdrClassCode String classCode, @ValidatorAdrDescription String description) {}

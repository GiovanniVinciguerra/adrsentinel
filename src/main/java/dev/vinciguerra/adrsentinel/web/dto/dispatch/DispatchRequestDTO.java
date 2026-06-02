package dev.vinciguerra.adrsentinel.web.dto.dispatch;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) radice (Root Object) che incapsula la richiesta 
 * di ottimizzazione e assegnazione dei carichi (Dispatch Plan) per il trasporto 
 * di merci pericolose su strada.
 * <p>
 * All'interno dell'architettura REST di AdrSentinel, questo record rappresenta il payload 
 * primario in ingresso all'endpoint di pianificazione logistica (es. {@code POST /api/v1/dispatch/plan}). 
 * Agisce come aggregatore per una distinta di base (Bill of Lading) di materie ADR eterogenee 
 * che il client desidera spedire.
 * </p>
 * <p>
 * <b>Comportamento di Validazione (Cascade Validation):</b><br>
 * L'integrità strutturale e normativa di questa richiesta è garantita dal pattern Fail-Fast 
 * nativo di Jakarta Validation:
 * <ul>
 * <li>L'annotazione {@link NotEmpty} assicura che il motore decisionale non venga mai 
 * invocato con liste di carico vuote, prevenendo cicli a vuoto e inutili query al database.</li>
 * <li>L'annotazione {@link Valid} sulla lista abilita la <b>validazione a cascata</b>: 
 * Spring ispezionerà iterativamente ogni singolo elemento della lista, applicando a sua volta 
 * i vincoli stringenti definiti all'interno di {@link OnuItemRequestDTO} 
 * (come il controllo sui pesi netti e sui gruppi di imballaggio). Se anche una sola riga 
 * del carico presenta anomalie, l'intera richiesta viene respinta con un HTTP 400 Bad Request.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Scelta Architetturale:</b> L'adozione di un {@code record} garantisce che la lista 
 * in ingresso, una volta deserializzata dal framework, rimanga immutabile durante 
 * l'attraversamento dei vari Service Layer, prevenendo alterazioni accidentali dello stato 
 * durante il calcolo dei raggruppamenti per la matrice di segregazione.
 * </p>
 * @param items La lista delle merci pericolose (materie o oggetti) richieste per la spedizione. 
 * Non può essere vuota o nulla. Ogni elemento contiene le direttive ONU, 
 * i gruppi di imballaggio e le masse nette necessarie al calcolo della 
 * Regola dei 1000 punti (ADR cap. 1.1.3.6) e delle incompatibilità.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 * @see OnuItemRequestDTO
 */
public record DispatchRequestDTO(@NotEmpty(message = "Malformed paylod: Invalid argument. The list of goods cannot be empty") List<@Valid OnuItemRequestDTO> items) {}

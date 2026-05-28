package dev.vinciguerra.adrsentinel.web.dto.adrdispatch;

import java.util.List;

/**
 * Data Transfer Object (DTO) radice (Root Response) che incapsula l'esito finale 
 * dell'elaborazione algoritmica di ottimizzazione dei trasporti ADR.
 * <p>
 * All'interno dell'ecosistema AdrSentinel, questo record rappresenta l'intero 
 * "Piano di Spedizione" (Dispatch Plan) generato dal motore decisionale. Costituisce 
 * il payload della risposta definitiva (tipicamente associato a un HTTP 200 OK) 
 * restituita dal controller al client, fornendo la mappatura completa e validata 
 * per l'evasione dell'ordine logistico.
 * </p>
 * <p>
 * <b>Design Pattern e Best Practice REST:</b><br>
 * L'incapsulamento della collezione {@code dispatches} all'interno di un oggetto radice 
 * (invece di restituire direttamente una {@code List} al client) è una scelta 
 * architetturale strategica per due motivi principali:
 * <ul>
 * <li><b>Estensibilità (Backward Compatibility):</b> Permette l'evoluzione dell'API. 
 * In futuro, sarà possibile aggiungere nuovi campi a livello radice (es. {@code totalFleetCost}, 
 * {@code routingCalculationTimeMs}, o flag globali di warning) senza rompere 
 * il contratto JSON per i client già in produzione.</li>
 * <li><b>Sicurezza (JSON Vulnerabilities):</b> Evita le storiche vulnerabilità di sicurezza 
 * legate al <i>JSON Hijacking</i>, che colpiscono gli endpoint che restituiscono 
 * JSON Array al top-level. Restituire un JSON Object ({@code { "dispatches": [...] }}) 
 * neutralizza nativamente questa criticità.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Immutabilità:</b> Come ogni record Java, questo oggetto garantisce la totale 
 * immutabilità in memoria, rendendo la fase di serializzazione da parte di Jackson 
 * (o Gson) intrinsecamente Thread-Safe e altamente performante.
 * </p>
 * @param dispatches La collezione completa e ordinata delle assegnazioni veicolo-carico. 
 * Ogni elemento rappresenta una singola missione di trasporto 
 * (Vehicle Routing), completa di distinta base delle merci, 
 * calcolo del peso e applicabilità delle esenzioni normative.
 * Se l'algoritmo non riceve input validi o viene invocato a vuoto, 
 * può restituire una lista vuota.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 * @see VehicleDispatchResponseDTO
 */
public record DispatchResponseDTO(List<VehicleDispatchResponseDTO> dispatches) {}

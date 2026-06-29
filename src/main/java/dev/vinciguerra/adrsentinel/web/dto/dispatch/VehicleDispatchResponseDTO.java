package dev.vinciguerra.adrsentinel.web.dto.dispatch;

import java.util.List;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleResponseDTO;

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
public record VehicleDispatchResponseDTO(List<VehicleAssignmentResponseDTO> dispatches) {
	/**
	 * Data Transfer Object (DTO) immutabile che rappresenta il risultato del processo 
	 * di ottimizzazione e assegnazione (Matchmaking logistico) per un singolo mezzo di trasporto.
	 * <p>
	 * Nel contesto architetturale di AdrSentinel, questo record incapsula la direttiva di carico 
	 * definitiva per un veicolo. Viene generato dal Service Layer al termine di due fasi computazionali critiche:
	 * <ol>
	 * <li><b>Fase di Segregazione (Clustering):</b> Le merci raggruppate in questo DTO sono già state 
	 * validate contro la matrice di incompatibilità ADR. È garantito normativamente che possano 
	 * viaggiare sullo stesso mezzo (assenza di divieto di carico in comune).</li>
	 * <li><b>Fase di Matchmaking:</b> Il veicolo assegnato (identificato dalla targa) possiede la 
	 * portata utile e le certificazioni (es. FL, AT, EX) necessarie per questo specifico set di merci.</li>
	 * </ol>
	 * </p>
	 * <p>
	 * <b>Uso tipico nell'API:</b> Questo oggetto non viaggia quasi mai da solo, ma costituisce 
	 * l'elemento portante di una collezione all'interno della response radice 
	 * (es. {@code DispatchPlanResponseDTO}), mappando l'intera flotta necessaria a evadere una spedizione.
	 * </p>
	 * @param assignedVehicleDTO DTO del veicolo commerciale selezionato dal motore decisionale per questo specifico carico.
	 * @param assignedOnuCodes La lista dei Codici ONU (es. "1263", "1090") delle merci assegnate a questo veicolo, passate come DTO.
	 * Il frontend può utilizzare questa lista per generare la distinta di carico 
	 * (Lettera di Vettura ADR) specifica per l'autista di questo mezzo.
	 * @param totalPayloadWeightkg Il peso netto totale (in chilogrammi) delle merci pericolose caricate sul veicolo. 
	 * Fondamentale per i controlli di sicurezza stradale e per evitare il sovraccarico 
	 * rispetto alla massa complessiva a pieno carico consentita (PTT).
	 * @param isExempt Flag booleano ad altissimo valore di business. Indica se il calcolo matematico 
	 * delle Categorie di Trasporto per questo specifico carico risulta inferiore o uguale 
	 * alla soglia normativa (Regola dei 1000 Punti - cap. 1.1.3.6 ADR).
	 * <ul>
	 * <li>{@code true}: Il trasporto gode di esenzione parziale. Non sono richiesti 
	 * pannelli arancioni aperti, equipaggiamento completo, né il patentino ADR (CFP) 
	 * per l'autista. Genera un forte abbattimento dei costi operativi.</li>
	 * <li>{@code false}: Il trasporto è in regime ADR completo e richiede un autista 
	 * specializzato e un mezzo pienamente equipaggiato.</li>
	 * </ul>
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (Strict Validated Input Payload)
	 * @since 1.0
	 */
	public record VehicleAssignmentResponseDTO(VehicleResponseDTO assignedVehicleDTO, List<String> assignedOnuCodes, Integer totalPayloadWeightkg,
		boolean isExempt) {}
}

package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;

/**
 * Data Transfer Object (DTO) radice utilizzato per deserializzare la risposta JSON 
 * generata dal motore di calcolo percorsi (Routing Engine) di OpenRouteService.
 * <p>
 * <b>Contesto Architetturale (Selective Deserialization & Anti-Corruption):</b><br>
 * L'API nativa di ORS restituisce un payload JSON estremamente prolisso, contenente metadati 
 * di esecuzione, bounding box (BBox), istruzioni di manovra passo-passo (Step-by-Step Navigation) 
 * e avvisi normativi (Warnings). Questo record implementa una strategia di <i>Deserializzazione Selettiva</i>: 
 * mappa esclusivamente l'albero essenziale (Summary e Geometry), ignorando il resto. 
 * Questo approccio agisce come un Anti-Corruption Layer, schermando l'applicazione dall'ingombro 
 * dei dati esterni e riducendo drasticamente il consumo di memoria RAM (Heap Memory) durante il parsing.
 * </p>
 * <p>
 * <b>Immutabilità (Thread-Safety):</b><br>
 * L'impiego dei Java Records nativi assicura che il risultato del calcolo della rotta sia 
 * rigidamente immutabile e sicuro per il passaggio tra i vari thread del Service Layer.
 * </p>
 * @param routes La lista dei percorsi alternativi calcolati dall'algoritmo. 
 * Solitamente, il sistema estrae l'elemento all'indice 0 (la "Best Route"), 
 * ovvero il percorso primario ottimizzato in base alle restrizioni fornite.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
public record OrsRouteResponseDTO(List<Route> routes) {
	/**
	 * Rappresenta una singola istanza di percorso stradale calcolato.
	 * Incapsula sia la matematica vettoriale della rotta sia le sue metriche aggregate.
	 * @param summary L'oggetto contenente i Key Performance Indicators (KPI) fisici e temporali dell'intero viaggio.
	 * @param geometry La stringa cartografica compressa formattata secondo l'algoritmo matematico <b>Encoded Polyline</b>. 
	 * Questa stringa viene passata intatta al client frontend per delegare al browser il rendering 
	 * della rotta direttamente sulla mappa visiva (es. Leaflet, OpenLayers).
	 */
	public record Route(Summary summary, String geometry) {}
	
	/**
	 * Raccoglie le misurazioni fisiche e temporali totali calcolate per la rotta associata.
	 * <p>
	 * <b>Nota Fondamentale sul Dominio (Unità di Misura):</b><br>
	 * Il provider esterno (OpenRouteService) lavora nativamente con le unità di misura base del 
	 * Sistema Internazionale (SI). Pertanto, i valori estratti da questo record non sono ancora 
	 * pronti per l'uso in ambito logistico umano. L'Orchestrator Service ha la responsabilità 
	 * architettonica di convertire questi dati prima di inietterli nel database:
	 * <ul>
	 * <li>La {@code distance} dovrà essere divisa per 1000 per ottenere i Chilometri (Km).</li>
	 * <li>La {@code duration} dovrà essere divisa per 60 (ed eventualmente arrotondata) per ottenere l'ETA in Minuti.</li>
	 * </ul>
	 * </p>
	 * @param distance La lunghezza stradale effettiva totale del percorso, espressa in <b>Metri</b>.
	 * @param duration Il tempo di percorrenza totale stimato (Estimated Time of Arrival), espresso in <b>Secondi</b>.
	 */
	public record Summary(Float distance, Float duration) {}
}

package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;
import dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute.ShipmentRoute;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentResponseDTO;

/**
 * Data Transfer Object (DTO) aggregatore (Response Payload) utilizzato dal Presentation Layer 
 * per esporre al client la visione globale di una spedizione multi-tappa e delle sue relative tratte.
 * <p>
 * <b>Contesto Architetturale (Multi-Stop Routing):</b><br>
 * A seguito dell'evoluzione verso il routing a tappe multiple, questo DTO agisce come un 
 * "contenitore padre" (Wrapper). Non descrive più una singola rotta, ma aggrega un'intera 
 * collezione di segmenti sequenziali (Legs) associandoli ai dati della spedizione di origine.
 * </p>
 * <p>
 * <b>Design Pattern:</b><br>
 * Sfrutta il costrutto {@code record} di Java 14+ per garantire immutabilità nativa (Thread-Safety) 
 * e una serializzazione JSON pulita e sicura tramite le librerie standard (es. Jackson).
 * </p>
 * @param routes La lista sequenziale dei singoli segmenti di viaggio calcolati, dove l'ordine 
 * nella lista rispetta l'ordine di percorrenza fisica del veicolo.
 * @param shipment Il DTO contenente le informazioni anagrafiche e fisiche della spedizione 
 * associata (Destinatario, Veicolo, ecc.), opportunamente mascherato e convertito.
 * @author Giovanni Vinciguerra
 * @version 2.0 (Multi-Stop Validated Output Payload)
 * @since 1.0 
 */
public record ShipmentRouteResponseDTO(List<ShipmentRouteStageResponseDTO> routes, ShipmentResponseDTO shipment) {
	
	/**
	 * DTO Annidato (Nested Record) che rappresenta la singola tratta (Segmento/Leg) 
	 * all'interno di un viaggio multi-tappa.
	 * <p>
	 * Incapsula tutte le metriche spaziali e temporali calcolate dal motore vettoriale (ORS) 
	 * per lo spostamento tra due specifici Waypoint.
	 * </p>
	 * @param routeUUID L'identificativo pubblico univoco del segmento. Maschera la Primary Key del database per prevenire attacchi IDOR.
	 * @param originLat La latitudine esatta del punto di partenza di questa specifica tratta (formato WGS 84).
	 * @param originLng La longitudine esatta del punto di partenza di questa specifica tratta (formato WGS 84).
	 * @param destLat La latitudine esatta della destinazione di questa specifica tratta (formato WGS 84).
	 * @param destLng La longitudine esatta della destinazione di questa specifica tratta (formato WGS 84).
	 * @param distancekm La distanza stradale effettiva della tratta in chilometri, calcolata per il profilo mezzi pesanti.
	 * @param etaMinutes Il tempo di percorrenza stimato (ETA) per questa singola tratta, espresso in minuti.
	 * @param tunnelRestriction La restrizione gallerie ADR applicata durante il calcolo (es. "D_E", "NONE").
	 * @param geometry La stringa vettoriale compressa (Encoded Polyline) per renderizzare accuratamente 
	 * il tracciato di questo segmento su una mappa frontend (Leaflet/Google Maps).
	 */
	public record ShipmentRouteStageResponseDTO(Long id, String routeUUID, Double originLat, Double originLng, Double destLat, Double destLng, Float distancekm,
			Integer etaMinutes, String tunnelRestriction, String geometry) {
		
		/**
		 * Mapper statico (Static Factory Method) responsabile della conversione sicura 
		 * dall'entità di dominio (JPA) della singola tratta al DTO {@link ShipmentRouteStageResponseDTO}.
		 * <p>
		 * <b>Logiche di Protezione e Mappatura:</b>
		 * <ul>
		 * <li><b>Null-Safety:</b> Implementa un controllo difensivo immediato (Fail-Fast) per prevenire {@code NullPointerException}.</li>
		 * <li><b>Enum Flattening:</b> Converte l'Enum {@code TunnelRestriction} nel suo valore testuale nativo 
		 * (tramite {@code .name()}) garantendo una serializzazione JSON immediata e sicura.</li>
		 * </ul>
		 * </p>
		 * @param entity L'entità relazionale {@link ShipmentRoute} rappresentante il singolo segmento da convertire.
		 * @return Un DTO {@link ShipmentRouteStageResponseDTO} formattato per il payload di rete, 
		 * oppure {@code null} se l'entità in ingresso era nulla.
		 */
		public static ShipmentRouteStageResponseDTO fromEntity(ShipmentRoute entity) {
			if(entity == null)
				return null;
			
			return new ShipmentRouteStageResponseDTO(
				entity.getId(),
				entity.getRouteUUID(),
				entity.getOriginLat(),
				entity.getOriginLng(),
				entity.getDestLat(),
				entity.getDestLng(),
				entity.getDistanceKm(),
				entity.getEtaMinutes(),
				entity.getTunnelRestriction().name(),
				entity.getGeometry()
			);
		}
	}
	
	/**
	 * Mapper statico aggregatore responsabile della costruzione del DTO padre.
	 * <p>
	 * <b>Logiche di Mappatura (Stream API):</b><br>
	 * Riceve la lista grezza di entità JPA relative ai vari segmenti e sfrutta le Stream API 
	 * per invocare iterativamente il mapper del singolo segmento ({@code fromEntity(ShipmentRoute)}).
	 * </p>
	 * @param entity La lista delle entità di database {@link ShipmentRoute} calcolate e persistite.
	 * @param shipment L'entità padre {@link Shipment} che ha originato la richiesta.
	 * @return Una nuova istanza immutabile di {@link ShipmentRouteResponseDTO}, oppure {@code null} 
	 * se la lista delle tratte fornita in input è nulla o vuota.
	 */
	public static ShipmentRouteResponseDTO fromEntity(List<ShipmentRoute> entity, Shipment shipment) {
		if(entity == null || entity.isEmpty())
			return null;
		
		return new ShipmentRouteResponseDTO(
			entity.stream().map(ShipmentRouteStageResponseDTO::fromEntity).toList(),
			ShipmentResponseDTO.fromEntity(shipment)
		);
	}
}

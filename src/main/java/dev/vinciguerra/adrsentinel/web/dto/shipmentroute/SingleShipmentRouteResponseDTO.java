package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute.ShipmentRoute;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentResponseDTO;

/**
 * Data Transfer Object (Response Payload) utilizzato dal Presentation Layer per esporre 
 * i dettagli completi e isolati di un singolo segmento di rotta logistica (Leg).
 * <p>
 * <b>Contesto Architetturale (Deep Mapping):</b><br>
 * A differenza del DTO utilizzato per le liste (che restituisce informazioni più compresse), 
 * questo Record viene impiegato per le chiamate puntuali (es. {@code GET /{routeUUID}} o 
 * come risposta a una {@code PUT}). Oltre alle metriche vettoriali, aggrega dinamicamente 
 * l'intero contesto dell'entità madre (tramite {@link ShipmentResponseDTO}), fornendo al 
 * client una visione a 360 gradi del viaggio senza richiedere ulteriori chiamate di rete.
 * </p>
 * <p>
 * <b>Sicurezza e Isolamento (Anti-IDOR):</b><br>
 * Questo DTO omette intenzionalmente la chiave primaria ({@code id}) del database relazionale, 
 * esponendo esclusivamente il {@code routeUUID}. Questo pattern impedisce l'enumerazione 
 * delle risorse e protegge l'ecosistema da attacchi di tipo Insecure Direct Object Reference.
 * </p>
 * @param routeUUID L'identificativo alfanumerico pubblico che maschera l'accesso al record.
 * @param originLat Latitudine esatta del punto di partenza della tratta (WGS 84).
 * @param originLng Longitudine esatta del punto di partenza della tratta (WGS 84).
 * @param destLat Latitudine esatta della destinazione della tratta (WGS 84).
 * @param destLng Longitudine esatta della destinazione della tratta (WGS 84).
 * @param distancekm La distanza stradale ricalcolata al netto di eventuali deviazioni HGV (Heavy Goods Vehicle).
 * @param etaMinutes Il tempo di percorrenza stimato espresso in minuti.
 * @param tunnelRestriction Il codice ADR della restrizione gallerie (es. "C/E"). Restituisce {@code null} se assente.
 * @param geometry La stringa vettoriale compressa (Encoded Polyline) per il rendering su mappa.
 * @param shipment L'oggetto annidato (Nested DTO) che espone i dettagli anagrafici, normativi 
 * e di stato della spedizione a cui questa tratta appartiene.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0 
 */
public record SingleShipmentRouteResponseDTO(String routeUUID, Double originLat, Double originLng, Double destLat, Double destLng, Float distancekm,
		Integer etaMinutes, String tunnelRestriction, String geometry, ShipmentResponseDTO shipment) {
	
	/**
	 * Mapper statico (Static Factory Method) responsabile della conversione dall'entità di 
	 * dominio JPA al DTO di risposta, comprensivo della risoluzione delle dipendenze annidate.
	 * <p>
	 * <b>Logiche di Protezione e Mappatura:</b>
	 * <ul>
	 * <li><b>Null-Safety:</b> Ritorna {@code null} in modo sicuro se l'entità sorgente è assente.</li>
	 * <li><b>Enum Flattening Protetto:</b> Converte l'enumeratore {@code TunnelRestriction} nel suo 
	 * valore testuale tramite un operatore ternario. Questo previene i crash (NPE) qualora 
	 * la tratta non sia soggetta ad alcuna restrizione ADR.</li>
	 * <li><b>Nested Mapping:</b> Invoca a cascata il mapper di {@link ShipmentResponseDTO} 
	 * per travasare le informazioni dell'entità genitore in totale sicurezza.</li>
	 * </ul>
	 * </p>
	 * @param entity L'entità {@link ShipmentRoute} da serializzare, estratta dal Persistence Context.
	 * @return Un DTO immutabile, epurato da logiche di persistenza e pronto per il dispatch JSON.
	 */
	public static SingleShipmentRouteResponseDTO fromEntity(ShipmentRoute entity) {
		if(entity == null)
			return null;
		
		return new SingleShipmentRouteResponseDTO(
			entity.getRouteUUID(),
			entity.getOriginLat(),
			entity.getOriginLng(),
			entity.getDestLat(),
			entity.getDestLng(),
			entity.getDistanceKm(),
			entity.getEtaMinutes(),
			entity.getTunnelRestriction().name(),
			entity.getGeometry(),
			ShipmentResponseDTO.fromEntity(entity.getShipment())
		);
	}
}

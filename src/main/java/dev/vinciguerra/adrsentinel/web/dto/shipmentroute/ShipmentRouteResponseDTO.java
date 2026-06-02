package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute.ShipmentRoute;
import dev.vinciguerra.adrsentinel.web.dto.shipment.ShipmentResponseDTO;

/**
 * Data Transfer Object (DTO) di uscita (Response Payload) utilizzato dal Presentation Layer 
 * per esporre al client i dettagli completi di una rotta logistica calcolata e salvata.
 * <p>
 * <b>Contesto Architetturale:</b><br>
 * Questo record rappresenta la "fotografia" immutabile e sicura di una rotta. 
 * Viene restituito dalle API REST (es. GET o post-creazione) per fornire al frontend 
 * (es. un'applicazione web o mobile) tutte le informazioni necessarie per il tracciamento, 
 * inclusa la renderizzazione della mappa e le direttive ADR, senza esporre la logica o 
 * la struttura interna del Database.
 * </p>
 * <p>
 * <b>Design Pattern:</b><br>
 * Utilizza la struttura nativa {@code record} di Java per garantire l'immutabilità assoluta 
 * (Thread-Safety) dei dati in fase di serializzazione JSON tramite Jackson.
 * </p>
 * @param routeUUID L'identificativo pubblico univoco della rotta. Sostituisce l'ID numerico 
 * del database (Primary Key) per prevenire attacchi di tipo IDOR (Insecure Direct Object Reference).
 * @param originLat La latitudine esatta del punto di partenza (formato WGS 84).
 * @param originLng La longitudine esatta del punto di partenza (formato WGS 84).
 * @param destLat La latitudine esatta della destinazione finale (formato WGS 84).
 * @param destLng La longitudine esatta della destinazione finale (formato WGS 84).
 * @param distancekm La distanza stradale effettiva della rotta espressa in chilometri, 
 * calcolata dal motore di routing tenendo conto dei vincoli HGV.
 * @param etaMinutes Il tempo stimato di arrivo (Estimated Time of Arrival) espresso in minuti.
 * @param tunnelRestriction Il codice testuale della restrizione gallerie ADR applicata all'intero 
 * veicolo (es. "C/E", "NONE"). Derivato dal calcolo della merce più pericolosa a bordo.
 * @param geometry La stringa vettoriale compressa (Encoded Polyline) generata dal motore cartografico. 
 * Consente al frontend di disegnare accuratamente il tracciato su una mappa (es. Leaflet/Google Maps).
 * @param shipment Il DTO annidato contenente le informazioni della spedizione associata (Destinatario, Veicolo, ecc.),
 * opportunamente mascherato tramite {@link ShipmentResponseDTO}.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record ShipmentRouteResponseDTO(String routeUUID, Double originLat, Double originLng, Double destLat, Double destLng, Float distancekm,
		Integer etaMinutes, String tunnelRestriction, String geometry, ShipmentResponseDTO shipment) {
	
	/**
	 * Mapper statico (Static Factory Method) responsabile della conversione sicura 
	 * dall'entità di dominio (JPA) al Data Transfer Object.
	 * <p>
	 * <b>Logiche di Mappatura:</b>
	 * <ul>
	 * <li><b>Null-Safety:</b> Implementa un controllo difensivo immediato. Se l'entità sorgente è nulla, 
	 * restituisce {@code null} prevenendo {@code NullPointerException} a cascata.</li>
	 * <li><b>Enum Flattening:</b> Converte l'Enum interno {@code TunnelRestriction} nel suo valore testuale 
	 * (tramite {@code .name()}) per garantire una perfetta serializzazione JSON compatibile con i client.</li>
	 * <li><b>Deep Mapping:</b> Invoca a sua volta il mapper statico di {@code ShipmentResponseDTO} 
	 * per convertire l'entità relazionata in modo ricorsivo e sicuro.</li>
	 * </ul>
	 * </p>
	 * @param entity L'entità di database {@link ShipmentRoute} da convertire. Può essere null.
	 * @return Una nuova istanza immutabile di {@link ShipmentRouteResponseDTO} pronta per la rete, 
	 * oppure {@code null} se l'entità in ingresso era nulla.
	 */
	public static ShipmentRouteResponseDTO fromEntity(ShipmentRoute entity) {
		if(entity == null)
			return null;
		
		return new ShipmentRouteResponseDTO(
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

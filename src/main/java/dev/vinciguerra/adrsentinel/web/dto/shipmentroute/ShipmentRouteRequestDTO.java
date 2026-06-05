package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorTunnelRestriction;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorDistance;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorETA;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorGeometry;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorLatitude;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorLongitude;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) utilizzato per persistere in modo definitivo 
 * i dati geospaziali e logistici di un percorso all'interno del database.
 * <p>
 * <b>Contesto Architetturale (Calculate-and-Confirm Pattern):</b><br>
 * Questo oggetto rappresenta il payload finale inviato dal client nella fase di "Conferma" 
 * (Commit Transazionale). Contiene i dati precedentemente elaborati dal motore di routing 
 * (es. OpenRouteService) e validati dall'utente. Mappa le informazioni strettamente necessarie 
 * per popolare l'entità {@code ShipmentRoute} prima del salvataggio definitivo sul database.
 * </p>
 * <p>
 * <b>Sicurezza e Validazione (Defensive Programming):</b><br>
 * Ogni campo sensibile è blindato da un'annotazione di validazione custom (JSR-380). 
 * Questo approccio "Zero-Trust" garantisce che nessuna coordinata fuori scala, tempo di 
 * percorrenza irragionevole o geometria malevola possa mai raggiungere il Service Layer 
 * o corrompere il database relazionale.
 * </p>
 * @param routeDetails Contiene tutti i dettagli dei segmenti della rotta da inserire.
 * @param routeUUID Il codice univoco che identifica la ShipmentRoute. In questo caso serve perchè l'algoritmo di 
 * routing ritorna al client un oggetto ShipmentRoute con UUID già formato ma non lo salva aspettando che sia il client 
 * a inviare una nuova richiesta (proprio questo record) per la memoriazzazione.
 * @param originLat La latitudine esatta del punto di partenza. Il vincolo {@code @ValidatorLatitude} 
 * assicura che il valore sia compreso tra -90.0 e 90.0 e previene anomalie matematiche (NaN/Infinity).
 * @param originLng La longitudine esatta del punto di partenza. Il vincolo {@code @ValidatorLongitude} 
 * assicura che il valore sia compreso tra -180.0 e 180.0 in modalità <i>Strict</i> (null non ammessi).
 * @param destLat La latitudine esatta del punto di arrivo, sottoposta ai medesimi controlli geografici dell'origine.
 * @param destLng La longitudine esatta del punto di arrivo, sottoposta ai medesimi controlli geografici dell'origine.
 * @param distancekm La distanza stradale effettiva calcolata al netto delle deviazioni obbligatorie. 
 * Il vincolo {@code @ValidatorDistance} assicura la coerenza metrica del viaggio.
 * @param etaMins Il tempo stimato di arrivo (Estimated Time of Arrival) espresso in minuti. 
 * Il vincolo {@code @ValidatorETA} garantisce che il viaggio sia superiore a 0 e non 
 * ecceda la soglia di sicurezza architetturale (es. 30 giorni) per prevenire Integer Overflow.
 * @param tunnelRestriction Il codice di restrizione gallerie ADR applicato attivamente alla rotta 
 * (es. "B", "C/E"). Validato da {@code @ValidatorTunnelRestriction} per accertarne 
 * la conformità alla normativa vigente. Può tollerare l'assenza (null) in caso di merci in esenzione.
 * @param geometry La stringa vettoriale compressa che rappresenta il tracciato su mappa. 
 * Il vincolo {@code @ValidatorGeometry} agisce da scudo crittografico e dimensionale, 
 * verificando l'aderenza all'algoritmo <i>Encoded Polyline</i> e bloccando attacchi DoS o Injection.
 * @param shipmentTrackingNumber Il tracking number della spedizione a cui è collegata questa ShipmentRoute
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentRouteRequestDTO(@NotEmpty(message = "Malformed payload: routes are required.") List<ShipmentRouteDetailDTO> routeDetails, @ValidatorUUID String shipmentTrackingNumber) {
	/**
	 * Data Transfer Object (DTO) di input (Request Payload) utilizzato per la conferma e 
	 * la memorizzazione definitiva di un singolo segmento di rotta logistica (Leg).
	 * <p>
	 * <b>Contesto Architetturale (Deferred Persistence Pattern):</b><br>
	 * Questo record supporta una logica transazionale a due fasi ("Calcola-e-Conferma"). 
	 * Durante la fase iniziale di routing cartografico, l'algoritmo agisce in modalità <i>stateless</i>: 
	 * fraziona il viaggio, calcola le metriche e genera un UUID identificativo per ogni segmento, 
	 * ma <b>non</b> altera lo stato del database. È responsabilità del client, a valle della 
	 * ricezione e dell'eventuale approvazione della rotta, inviare nuovamente questo payload 
	 * al server per innescare la reale transazione SQL (INSERT).
	 * </p>
	 * <p>
	 * <b>Sicurezza e Zero-Trust Policy:</b><br>
	 * Sebbene i dati trasportati da questo DTO siano originati dal server stesso nella precedente 
	 * fase di calcolo, il layer di presentazione adotta un paradigma "Zero-Trust". Per prevenire 
	 * tentativi di manomissione client-side, ogni singolo attributo viene sottoposto a una 
	 * validazione custom rigorosa prima che l'entità possa accedere al Persistence Context.
	 * </p>
	 * @param routeUUID Il codice univoco che identifica la ShipmentRoute. In questo caso serve perchè l'algoritmo di 
	 * routing ritorna al client un oggetto ShipmentRoute con UUID già formato ma non lo salva aspettando che sia il client 
	 * a inviare una nuova richiesta (proprio questo record) per la memoriazzazione.
	 * @param originLat La latitudine esatta del punto di partenza. Il vincolo {@code @ValidatorLatitude} 
	 * assicura che il valore sia compreso tra -90.0 e 90.0 e previene anomalie matematiche (NaN/Infinity).
	 * @param originLng La longitudine esatta del punto di partenza. Il vincolo {@code @ValidatorLongitude} 
	 * assicura che il valore sia compreso tra -180.0 e 180.0 in modalità <i>Strict</i> (null non ammessi).
	 * @param destLat La latitudine esatta del punto di arrivo, sottoposta ai medesimi controlli geografici dell'origine.
	 * @param destLng La longitudine esatta del punto di arrivo, sottoposta ai medesimi controlli geografici dell'origine.
	 * @param distancekm La distanza stradale effettiva calcolata al netto delle deviazioni obbligatorie. 
	 * Il vincolo {@code @ValidatorDistance} assicura la coerenza metrica del viaggio.
	 * @param etaMins Il tempo stimato di arrivo (Estimated Time of Arrival) espresso in minuti. 
	 * Il vincolo {@code @ValidatorETA} garantisce che il viaggio sia superiore a 0 e non 
	 * ecceda la soglia di sicurezza architetturale (es. 30 giorni) per prevenire Integer Overflow.
	 * @param tunnelRestriction Il codice di restrizione gallerie ADR applicato attivamente alla rotta 
	 * (es. "B", "C/E"). Validato da {@code @ValidatorTunnelRestriction} per accertarne 
	 * la conformità alla normativa vigente. Può tollerare l'assenza (null) in caso di merci in esenzione.
	 * @param geometry La stringa vettoriale compressa che rappresenta il tracciato su mappa. 
	 * Il vincolo {@code @ValidatorGeometry} agisce da scudo crittografico e dimensionale, 
	 * verificando l'aderenza all'algoritmo <i>Encoded Polyline</i> e bloccando attacchi DoS o Injection.
	 * @param shipmentTrackingNumber Il tracking number della spedizione a cui è collegata questa ShipmentRoute
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (Strict Validated Input Payload)
	 * @since 1.0
	 */
	public record ShipmentRouteDetailDTO(@ValidatorUUID String routeUUID, @ValidatorLatitude Double originLat, @ValidatorLongitude Double originLng, @ValidatorLatitude Double destLat,
		@ValidatorLongitude Double destLng, @ValidatorDistance Float distancekm, @ValidatorETA Integer etaMins, @ValidatorTunnelRestriction String tunnelRestriction,
		@ValidatorGeometry String geometry) {}
}

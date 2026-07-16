package dev.vinciguerra.adrsentinel.db.shipmentroute;

import java.util.Objects;
import java.util.UUID;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entità che rappresenta il percorso geospaziale ed operativo calcolato per una determinata spedizione.
 * <p>
 * Questa classe memorizza i risultati fisici e matematici generati dal motore di routing esterno 
 * (es. OpenRouteService). Agisce come "gemello cartografico" della spedizione logistica, 
 * incapsulando le coordinate esatte, i tempi di percorrenza e la polilinea per la renderizzazione visiva.
 * </p>
 * <p>
 * <b>Scelte Architetturali:</b>
 * <ul>
 * <li><b>Separazione dei concetti (SoC):</b> Mantiene l'entità {@link Shipment} leggera, isolando i pesanti dati 
 * spaziali in questa tabella correlata tramite una relazione Uno-a-Uno.</li>
 * <li><b>Tracciabilità Normativa:</b> Salva il codice galleria ({@code appliedTunnelCode}) effettivamente 
 * utilizzato per il calcolo, garantendo la storicizzazione in caso di controlli delle autorità.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation)
 * @since 1.0
 */
@Entity
@Table(
	name = "shipment_route",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_route_uuid",
			columnNames = {"route_uuid"}
		)
	}
)
public class ShipmentRoute {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Chiave di Business (Surrogate Business Key).
	 * Generata istantaneamente per garantire l'uguaglianza logica nell'ORM indipendentemente dallo stato di salvataggio.
	 */
	@Column(
		name = "route_uuid",
		nullable = false,
		unique = true,
		updatable = false,
		length = 36
	)
	private String routeUUID = UUID.randomUUID().toString();
	/** Latitudine esatta del punto di partenza elaborata dal Geocoder. */
	@Column(
		name = "origin_latitude",
		nullable = false
	)
	private Double originLat;
	/** Longitudine esatta del punto di partenza elaborata dal Geocoder. */
	@Column(
		name = "origin_longitude",
		nullable = false
	)
	private Double originLng;
	/** Latitudine esatta del punto di arrivo elaborata dal Geocoder. */
	@Column(
		name = "destination_latitude",
		nullable = false
	)
	private Double destLat;
	/** Longitudine esatta del punto di arrivo elaborata dal Geocoder. */
	@Column(
		name = "destination_longitude",
		nullable = false
	)
	private Double destLng;
	/** Distanza effettiva del tragitto su strada (non in linea d'aria), espressa in chilometri. */
	@Column(
		name = "distance_km",
		nullable = false,
		scale = 3
	)
	private Float distanceKm;
	/** Tempo di Arrivo Stimato (Estimated Time of Arrival), espresso in minuti. Calcolato tenendo conto dei limiti di velocità per i mezzi pesanti. */
	@Column(
		name = "eta_minutes",
		nullable = false
	)
	private Integer etaMinutes;
	/**
	 * Stringa codificata (Encoded Polyline) restituita da OpenRouteService.
	 * <p>
	 * Contiene la sequenza di migliaia di coordinate GPS necessarie per tracciare la linea blu 
	 * sulla mappa del frontend. Viene utilizzato {@code columnDefinition = "TEXT"} poiché 
	 * le polilinee per lunghi tragitti superano abbondantemente il limite standard di 255 caratteri dei VARCHAR.
	 * </p>
	 */
	@Lob
	@Column(
		name = "geometry",
		nullable = false,
		columnDefinition = "TEXT"
	)
	private String geometry;
	/** Relazione Uno-a-Uno con la spedizione. */
	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "shipment_id",
		nullable = false,
		unique = true,
		foreignKey = @ForeignKey(name = "fk_route_shipment")
	)
	private Shipment shipment;
	
	public ShipmentRoute() {/* Costruttore volutamente lasciato vuoto */}
	
	/**
	 * Costruttore architetturale progettato specificamente per supportare il pattern di 
	 * Persistenza Differita ("Calcola-e-Conferma") nel ciclo di vita di una rotta.
	 * <p>
	 * <b>Flusso Operativo (Stateless Routing):</b>
	 * <ol>
	 * <li><b>Calcolo:</b> Il server elabora il percorso ottimizzato basandosi sui vincoli del veicolo e della merce ADR.</li>
	 * <li><b>Allocazione:</b> Viene istanziato un oggetto transitorio {@code ShipmentRoute} a cui viene 
	 * assegnato un UUID generato a runtime.</li>
	 * <li><b>Esposizione:</b> L'oggetto viene restituito al client tramite payload di risposta senza essere persistito nel DB.</li>
	 * <li><b>Conferma e Persistenza:</b> A valle dell'approvazione dell'utente, il client invia una richiesta 
	 * di salvataggio (INSERT) allegando l'UUID originale. Questo costruttore permette al backend di ricostruire 
	 * l'entità ricollegandola all'identificativo precedentemente generato.</li>
	 * </ol>
	 * </p>
	 * <p>
	 * <b>Integrità del Dominio (Immutabilità):</b><br>
	 * L'utilizzo di questo costruttore parametrico sopperisce all'assenza intenzionale di un metodo 
	 * {@code setRouteUUID()}. L'identificativo pubblico è una <i>Business Key</i>: una volta 
	 * iniettato al momento dell'istanziazione, non può in alcun modo essere alterato, garantendo 
	 * la sicurezza e la coerenza del dato.
	 * </p>
	 * @param routeUUID L'identificativo alfanumerico univoco precedentemente calcolato dal server 
	 * e ri-sottomesso dal client per innescare la persistenza definitiva.
	 */
	public ShipmentRoute(String routeUUID) {
		this.routeUUID = routeUUID;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getRouteUUID() {
		return routeUUID;
	}

	public Double getOriginLat() {
		return originLat;
	}

	public void setOriginLat(Double originLat) {
		this.originLat = originLat;
	}

	public Double getOriginLng() {
		return originLng;
	}

	public void setOriginLng(Double originLng) {
		this.originLng = originLng;
	}

	public Double getDestLat() {
		return destLat;
	}

	public void setDestLat(Double destLat) {
		this.destLat = destLat;
	}

	public Double getDestLng() {
		return destLng;
	}

	public void setDestLng(Double destLng) {
		this.destLng = destLng;
	}

	public Float getDistanceKm() {
		return distanceKm;
	}

	public void setDistanceKm(Float distanceKm) {
		this.distanceKm = distanceKm;
	}

	public Integer getEtaMinutes() {
		return etaMinutes;
	}

	public void setEtaMinutes(Integer etaMinutes) {
		this.etaMinutes = etaMinutes;
	}

	public String getGeometry() {
		return geometry;
	}

	public void setGeometry(String geometry) {
		this.geometry = geometry;
	}

	public Shipment getShipment() {
		return shipment;
	}

	public void setShipment(Shipment shipment) {
		this.shipment = shipment;
	}

	@Override
	public int hashCode() {
		return Objects.hash(routeUUID);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ShipmentRoute other = (ShipmentRoute) obj;
		return Objects.equals(routeUUID, other.routeUUID);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ShipmentRoute [id=").append(id).append(", routeUUID=").append(routeUUID).append(", originLat=")
			.append(originLat).append(", originLng=").append(originLng).append(", destLat=").append(destLat)
			.append(", destLng=").append(destLng).append(", distanceKm=").append(distanceKm).append(", etaMinutes=")
			.append(etaMinutes).append(", geometry=").append(geometry).append("]");
		return builder.toString();
	}
}

package dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute;

import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.ColumnDefault;

import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
	@Column(name = "origin_latitude", nullable = false)
	private Double originLat;
	/** Longitudine esatta del punto di partenza elaborata dal Geocoder. */
	@Column(name = "origin_longitude", nullable = false)
	private Double originLng;
	/** Latitudine esatta del punto di arrivo elaborata dal Geocoder. */
	@Column(name = "destination_latitude", nullable = false)
	private Double destLat;
	/** Longitudine esatta del punto di arrivo elaborata dal Geocoder. */
	@Column(name = "destination_longitude", nullable = false)
	private Double destLng;
	/** Distanza effettiva del tragitto su strada (non in linea d'aria), espressa in chilometri. */
	@Column(name = "distance_km", nullable = false, scale = 3)
	private Float distanceKm;
	/**
	 * Tempo di Arrivo Stimato (Estimated Time of Arrival), espresso in minuti.
	 * Calcolato tenendo conto dei limiti di velocità per i mezzi pesanti.
	 */
	@Column(name = "eta_minutes", nullable = false)
	private Integer etaMinutes;
	/**
	 * Il divieto di transito in determinate gallerie. 
	 * Se null verrà associato il grado massimo {@code B}
	 */
	@Enumerated(EnumType.STRING)
    @Column(
    	name = "tunnel_restriction",
    	nullable = false,
    	length = 255
    )
	@ColumnDefault("'B'")
    private TunnelRestriction tunnelRestriction;
	/**
	 * Stringa codificata (Encoded Polyline) restituita da OpenRouteService.
	 * <p>
	 * Contiene la sequenza di migliaia di coordinate GPS necessarie per tracciare la linea blu 
	 * sulla mappa del frontend. Viene utilizzato {@code columnDefinition = "TEXT"} poiché 
	 * le polilinee per lunghi tragitti superano abbondantemente il limite standard di 255 caratteri dei VARCHAR.
	 * </p>
	 */
	@Lob
	@Column(name = "geometry", nullable = false, columnDefinition = "TEXT")
	private String geometry;
	/**
	 * Relazione Uno-a-Uno con la spedizione.
	 * <p>
	 * Utilizza {@code FetchType.LAZY} per evitare query cartesiane (N+1) indesiderate quando 
	 * si carica una singola Route, ritardando l'estrazione dello Shipment al momento dell'effettivo bisogno.
	 * </p>
	 */
	@OneToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "shipment_id",
		nullable = false,
		unique = true,
		foreignKey = @ForeignKey(name = "fk_route_shipment")
	)
	private Shipment shipment;
	
	/**
	 * Hook del ciclo di vita JPA (Lifecycle Callback) invocato automaticamente dall'ORM 
	 * prima di ogni inserimento ({@code @PrePersist}) o aggiornamento ({@code @PreUpdate}) nel database.
	 * <p>
	 * Questo metodo agisce da coordinatore per le operazioni di pre-salvataggio, garantendo 
	 * l'integrità dei dati geospaziali e la conformità normativa attraverso due fasi:
	 * </p>
	 * <ul>
	 * <li>
	 * <b>Politica di Sicurezza "Fail-Safe" (Tunnel Restriction):</b><br>
	 * Qualora il calcolo del routing non fornisca un codice di restrizione per le gallerie 
	 * ({@code tunnelRestriction == null}), il sistema attua un meccanismo di difesa preventiva 
	 * assegnando d'ufficio il valore di massima cautela ({@link TunnelRestriction#B}). 
	 * Questa scelta architetturale garantisce che, in caso di dati incompleti o ambigui, 
	 * il motore cartografico instradi il veicolo vietandogli l'accesso alla quasi totalità 
	 * dei tunnel (Categorie B, C, D ed E). Ciò previene alla radice il rischio di violazioni 
	 * del Codice della Strada e massimizza la sicurezza pubblica durante il trasporto ADR.
	 * </li>
	 * <li>
	 * <b>Sincronizzazione dello Stato Relazionale (Chilometraggio):</b><br>
	 * Propaga istantaneamente la distanza su strada calcolata ({@code distanceKm}) 
	 * all'interno dell'entità padre ({@link Shipment}). Eseguendo questa operazione 
	 * una frazione di secondo prima del commit transazionale, il sistema assicura che 
	 * la spedizione possegga sempre il dato metrico reale ed esatto, fondamentale per 
	 * i calcoli di fatturazione, le stime di consumo carburante e i report logistici, 
	 * mantenendo una perfetta coerenza tra le due tabelle.
	 * </li>
	 * </ul>
	 */
	@PrePersist
	@PreUpdate
	private void syncShipmentDistanceAndNormalize() {
		if(tunnelRestriction == null)
			tunnelRestriction = TunnelRestriction.B;
		if(shipment != null)
			shipment.setDistancekm(distanceKm);
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

	public void setRouteUUID(String routeUUID) {
		this.routeUUID = routeUUID;
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

	public TunnelRestriction getTunnelRestriction() {
		return tunnelRestriction;
	}

	public void setTunnelRestriction(TunnelRestriction tunnelRestriction) {
		this.tunnelRestriction = tunnelRestriction;
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
			.append(etaMinutes).append(", tunnelRestriction=").append(tunnelRestriction).append(", geometry=")
			.append(geometry).append(", shipment=").append(shipment).append("]");
		return builder.toString();
	}
}

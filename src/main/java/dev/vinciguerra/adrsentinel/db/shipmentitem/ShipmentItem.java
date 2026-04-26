package dev.vinciguerra.adrsentinel.db.shipmentitem;

import java.util.Objects;
import java.util.UUID;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Entità che modella una singola riga di carico (Dettaglio Merce Pericolosa) all'interno 
 * di una Spedizione ADR.
 * <p>
 * <b>Ruolo nel Dominio Logistico:</b><br>
 * Questa classe rappresenta l'associazione fisica tra una Spedizione, un Numero ONU 
 * (che classifica il pericolo) e la relativa quantità trasportata. È il fulcro per il 
 * calcolo dei limiti di peso, delle esenzioni ADR (es. regola dei 1000 punti) e della 
 * compatibilità chimica sul veicolo.
 * </p>
 * <p>
 * <b>Architettura JPA e Integrità (Il Pattern UUID):</b><br>
 * La classe implementa il pattern della <b>Surrogate Business Key</b>. Poiché non esiste 
 * una chiave naturale per identificare univocamente un bancale di merce (potrebbero esistere 
 * due righe identiche per lo stesso Numero ONU e quantità), viene generato un UUID al momento 
 * dell'istanziazione in memoria. Questo UUID:
 * <ul>
 * <li>È immutabile e protetto a livello SQL ({@code unique = true, updatable = false}).</li>
 * <li>Garantisce la totale stabilità dei metodi {@code equals()} e {@code hashCode()}, 
 * evitando che l'oggetto "sfugga" agli {@code HashSet} di Hibernate quando lo stato cambia.</li>
 * <li>Previene vulnerabilità di tipo IDOR se l'entità viene esposta tramite API REST.</li>
 * </ul>
 * </p>
 * <p>
 * <b>Ottimizzazione delle Relazioni:</b><br>
 * Tutte le relazioni esterne ({@link Shipment} e {@link OnuNumber}) sono rigorosamente 
 * impostate su {@code FetchType.LAZY} per prevenire problemi di N+1 queries e salvaguardare 
 * l'occupazione di memoria (RAM) durante le letture massimali.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (UUID Persistent Set Entity)
 * @since 1.0
 */
@Entity
@Table(
	name = "shipment_item",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_item_uuid", columnNames = {"item_uuid"})
	}
)
public class ShipmentItem {
	/**
	 * Enumerazione interna per garantire la coerenza semantica delle unità di misura,
	 * essenziale per normalizzare i calcoli normativi ADR (es. conversione volumi in pesi).
	 */
	public enum UnitOfMeasure {
		LITRE,
		KILOGRAM,
		CUBIC_METRE
	}
	
	/** Chiave primaria surrogata autogenerata. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Chiave di business surrogata. Generata istantaneamente alla chiamata del costruttore {@code new}.
	 * Questo valore accompagna l'oggetto dal suo stato <i>transient</i> fino alla sua 
	 * distruzione. Ha solo il getter per garantirne l'immutabilità assoluta.
	 */
	@Column(
		name = "item_uuid",
		nullable = false,
		unique = true,
		updatable = false,
		length = 36
	)
	private String itemUUID = UUID.randomUUID().toString();
	/** Quantità trasportata. */
	@NotNull(message = "Quantity cannot be null")
	@Positive(message = "Quantity must be strictly greater than zero")
	@Column(
		name = "quantity",
		nullable = false,
		scale = 3
	)
	private Float quantity;
	/**
	 * Unità di misura della quantità. Salvata come stringa sul database per prevenire 
	 * la corruzione dei dati in caso di alterazione dell'ordine dell'Enum.
	 */
	@NotNull(message = "Unit of measure cannot be null")
	@Enumerated(EnumType.STRING)
	@Column(
		name = "unit_of_measure",
		nullable = false,
		length = 255
	)
	private UnitOfMeasure unitOfMeasure;
	/**
	 * Riferimento al "Padre" (la Spedizione).
	 * La Foreign Key è esplicitamente nominata per garantire la leggibilità dei log di schema.
	 */
	@NotNull(message = "Shipment cannot be null")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
		name = "shipment_id",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_shipment_shipment_item")
	)
	private Shipment shipment;
	/**
	 * Riferimento alla natura della merce (Numero ONU).
	 * Determina le regole chimiche e i limiti quantitativi applicabili.
	 */
	@NotNull(message = "OnuNumber cannot be null")
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(
		name = "onu_number_id",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_shipment_onu_number")
	)
	private OnuNumber onuNumber;
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getItemUUID() {
		return itemUUID;
	}

	public Float getQuantity() {
		return quantity;
	}
	
	public void setQuantity(Float quantity) {
		this.quantity = quantity;
	}
	
	public UnitOfMeasure getUnitOfMeasure() {
		return unitOfMeasure;
	}
	
	public void setUnitOfMeasure(UnitOfMeasure unitOfMeasure) {
		this.unitOfMeasure = unitOfMeasure;
	}
	
	public Shipment getShipment() {
		return shipment;
	}
	
	public void setShipment(Shipment shipment) {
		this.shipment = shipment;
	}
	
	public OnuNumber getOnuNumber() {
		return onuNumber;
	}
	
	public void setOnuNumber(OnuNumber onuNumber) {
		this.onuNumber = onuNumber;
	}
	
	/**
	 * Genera un hash costante basato esclusivamente sull'{@link #itemUUID}.
	 * Questo previene la disconnessione dell'oggetto dalle collezioni {@code Set} di JPA 
	 * qualora campi come la quantità vengano modificati.
	 */
	@Override
	public int hashCode() {
		return Objects.hash(itemUUID);
	}
	
	/**
	 * L'uguaglianza logica è delegata in toto al Surrogate Business Key (UUID).
	 * Due istanze di {@link ShipmentItem} sono considerate identiche se e solo se 
	 * condividono lo stesso UUID originario.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ShipmentItem other = (ShipmentItem) obj;
		return Objects.equals(itemUUID, other.itemUUID);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("ShipmentItem [id=").append(id).append(", quantity=").append(quantity).append(", unitOfMeasure=")
			.append(unitOfMeasure).append("]");
		return builder.toString();
	}
}

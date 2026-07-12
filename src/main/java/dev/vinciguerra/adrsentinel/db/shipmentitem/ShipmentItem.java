package dev.vinciguerra.adrsentinel.db.shipmentitem;

import java.util.Objects;
import java.util.UUID;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem.PackageDetail.PackageType;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
	 * Componente di dominio (Value Object) integrabile che incapsula le specifiche logistico-fisiche 
	 * e normative relative all'imballaggio di una merce pericolosa.
	 * <p><b>Contesto Architetturale (JPA Embeddable &amp; Value Object):</b></p>
	 * Progettata per essere annotata come {@code @Embedded} all'interno di entità superiori 
	 * (es. le righe del Documento di Trasporto o del database logistico). I suoi attributi 
	 * non generano una tabella separata, ma vengono "spalmati" (flattened) direttamente 
	 * sulle colonne dell'entità ospite, ottimizzando le performance di lettura e scrittura sul database.
	 * <p><b>Design dell'Identità (Frontend Tracking):</b></p>
	 * Pur essendo un oggetto incassato, possiede una propria <i>Business Key</i> autogenerata 
	 * ({@code detailUUID}). Questa scelta permette di rintracciare e manipolare in modo univoco 
	 * le singole istanze a livello di Presentation Layer (es. React state management) e 
	 * protegge l'integrità delle strutture dati (Set, Map) superando le limitazioni standard 
	 * dei Value Object privi di ID primario.
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (UUID Persistent Set Entity)
	 * @since 1.0
	 */
	@Embeddable
	public static class PackageDetail {
		/**
		 * Enumerazione che codifica le macro-categorie fisiche degli imballaggi 
		 * ammessi nel trasporto logistico.
		 */
		public enum PackageType {
			/** Fusto (tipicamente cilindrico, in acciaio, plastica o cartone). */
			DRUM, // Fusto
			/** Intermediate Bulk Container (Cisternetta pallettizzata, tipicamente da 1000 litri). */
		    IBC, // Intermediate Bulk Container (Cisternetta)
		    /** Scatola (imballaggio esterno rettangolare/poligonale). */
		    BOX, // Scatola
		    /** Sacco (imballaggio flessibile, es. per merci in polvere). */
		    BAG, // Sacco
		    /** Tanica (imballaggio di piccola/media capacità con maniglia integrata). */
		    JERRICAN, // Tanica
		    /** Cisterna (trasporto allo stato fuso o per grandi quantità di gas/liquidi). */
		    TANK, // Cisterna
		    /** Sovraimballaggio logistico (es. un pallet filmato o reggiato contenente più colli separati). */
		    OVERPACK, // Sovraimballaggio (es. Pallet filmato con più colli)
		    /** Merce non imballata (es. articoli ingombranti, macchinari o trasporto alla rinfusa). */
		    UNPACKAGED // Non imballato (es. articoli ingombranti)
		}
		
		/**
		 * Identificativo univoco di tracciamento (UUID v4) per il singolo dettaglio di imballaggio.
		 * Generato nativamente all'istanziazione e marcato come strettamente immutabile ({@code updatable = false}).
		 */
		@Column(
			name = "detail_uuid",
			nullable = false,
			unique = true,
			updatable = false,
			length = 36
		)
		private String detailUUID = UUID.randomUUID().toString();
		/** Il numero totale di colli (unità fisiche) che compongono questo specifico blocco di imballaggio. */
		@Column(
			name = "package_count",
			nullable = false
		)
		private Integer packageCount;
		/**
		 * La classificazione fisica dell'imballaggio.
		 * Salvata sul database come stringa per facilitare la diagnostica e l'espandibilità futura.
		 */
		@Enumerated(EnumType.STRING)
		@Column(
			name = "package_type",
			nullable = false,
			length = 255
		)
		private PackageType packageType;
		/**
		 * Codice di omologazione ONU (es. "1A1", "31HA1", "4G").
		 * Indica il livello di resistenza del contenitore e la sua idoneità al trasporto 
		 * della specifica materia pericolosa. Limitato a 15 caratteri.
		 */
		@Column(
			name = "onu_packing_code",
			nullable = false,
			length = 15
		)
		private String onuPackingCode;
		/**
		 * Il peso della tara totale riferito a questo blocco di imballaggi, espresso in chilogrammi (kg).
		 * Fondamentale per la determinazione del Peso Lordo complessivo ai fini della conformità al Codice della Strada.
		 */
		@Column(
			name = "packaging_weight_kg",
			nullable = false
		)
		private Float packagingWeightkg;
		
		public String getDetailUUID() {
			return detailUUID;
		}

		public Integer getPackageCount() {
			return packageCount;
		}
		
		public void setPackageCount(Integer packageCount) {
			this.packageCount = packageCount;
		}
		
		public PackageType getPackageType() {
			return packageType;
		}
		
		public void setPackageType(PackageType packageType) {
			this.packageType = packageType;
		}
		
		public String getOnuPackingCode() {
			return onuPackingCode;
		}
		
		public void setOnuPackingCode(String onuPackingCode) {
			this.onuPackingCode = onuPackingCode;
		}
		
		public Float getPackagingWeightkg() {
			return packagingWeightkg;
		}
		
		public void setPackagingWeightkg(Float packagingWeightkg) {
			this.packagingWeightkg = packagingWeightkg;
		}
		
		/** Calcola l'hash code basandosi esclusivamente sulla Business Key immutabile ({@code detailUUID}). */
		@Override
		public int hashCode() {
			return Objects.hash(detailUUID);
		}
		
		/**
		 * Valuta l'uguaglianza logica tra due istanze confrontando unicamente 
		 * il {@code detailUUID}, rispettando il contratto di identità indipendente 
		 * per i Value Object tracciabili.
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			PackageDetail other = (PackageDetail) obj;
			return Objects.equals(detailUUID, other.detailUUID);
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("PackageDetail [packageCount=").append(packageCount).append(", packageType=")
				.append(packageType).append(", onuPackingCode=").append(onuPackingCode)
				.append(", packagingWeightkg=").append(packagingWeightkg).append("]");
			return builder.toString();
		}
	}
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
	@Column(
		name = "quantity",
		nullable = false
	)
	private Integer quantity;
	/** Peso in kilogrammi della quantità trasportata. */
	@Column(
		name = "net_weight_kg",
		nullable = false
	)
	private Integer netWeightkg;
	/**
	 * Unità di misura della quantità. Salvata come stringa sul database per prevenire 
	 * la corruzione dei dati in caso di alterazione dell'ordine dell'Enum.
	 */
	@Enumerated(EnumType.STRING)
	@Column(
		name = "unit_of_measure",
		nullable = false,
		length = 255
	)
	private UnitOfMeasure unitOfMeasure;
	@Embedded
	private PackageDetail packageDetails;
	/**
	 * Riferimento al "Padre" (la Spedizione).
	 * La Foreign Key è esplicitamente nominata per garantire la leggibilità dei log di schema.
	 */
	@ManyToOne(fetch = FetchType.EAGER)
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
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "onu_number_id",
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_shipment_onu_number")
	)
	private OnuNumber onuNumber;
	
	/**
	 * Intercetta gli eventi di ciclo di vita JPA (Salvataggio e Aggiornamento) per garantire 
	 * la coerenza logistica dei dati fisici di imballaggio prima della persistenza sul database.
	 * <p><b>Contesto Normativo (ADR & Dominio Logistico):</b></p>
	 * Nel trasporto merci, la tara (il peso del materiale di imballaggio) è fondamentale 
	 * per la corretta stesura del D.D.T. e per il calcolo dei pesi lordi. 
	 * Questo metodo implementa una <i>Guard Clause</i> che applica la seguente regola di business: 
	 * se la merce viaggia in un contenitore fisico (es. DRUM, BOX, IBC), il peso della tara 
	 * non può essere zero. L'assenza di peso è matematicamente tollerata <b>solo ed esclusivamente</b> 
	 * se la merce viaggia alla rinfusa ({@code UNPACKAGED}) o in cisterna ({@code TANK}), 
	 * scenari in cui il contenitore coincide con il mezzo di trasporto stesso.
	 * <p><b>Meccanismo di Validazione (Fail-Fast):</b></p>
	 * Sfruttando le annotazioni {@link PrePersist} e {@link PreUpdate}, questo controllo 
	 * viene scatenato automaticamente da Hibernate immediatamente prima di inviare l'istruzione 
	 * SQL di {@code INSERT} o {@code UPDATE}. Qualora l'invariante di dominio venga violata, 
	 * la transazione viene interrotta istantaneamente, prevenendo la corruzione dei dati.
	 * @throws IllegalShipmentStateException Se viene rilevata un'incongruenza logica tra il 
	 * tipo di imballaggio dichiarato e il peso della tara (es. un fusto con peso 0).
	 */
	@PrePersist
	@PreUpdate
	private void ensurePackageDetailWeight() throws IllegalShipmentStateException {
		if(packageDetails != null && packageDetails.packagingWeightkg == 0) {
			if(packageDetails.packageType != PackageType.UNPACKAGED && packageDetails.packageType != PackageType.TANK) 
				throw new IllegalShipmentStateException(
					String.format(
						"Data inconsistency: Packaging weight (tare) cannot be zero for package type '%s'. Zero is only permitted for UNPACKAGED or TANK shipments.",
						packageDetails.packageType.name()
					)
				);
		}
	}
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getItemUUID() {
		return itemUUID;
	}

	public Integer getQuantity() {
		return quantity;
	}
	
	public void setQuantity(Integer quantity) {
		this.quantity = quantity;
	}
	
	public Integer getNetWeightkg() {
		return netWeightkg;
	}

	public void setNetWeightkg(Integer netWeightkg) {
		this.netWeightkg = netWeightkg;
	}

	public UnitOfMeasure getUnitOfMeasure() {
		return unitOfMeasure;
	}
	
	public void setUnitOfMeasure(UnitOfMeasure unitOfMeasure) {
		this.unitOfMeasure = unitOfMeasure;
	}
	
	public PackageDetail getPackageDetails() {
		return packageDetails;
	}

	public void setPackageDetails(PackageDetail packageDetails) {
		this.packageDetails = packageDetails;
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
		builder.append("ShipmentItem [id=").append(id).append(", itemUUID=").append(itemUUID).append(", quantity=")
			.append(quantity).append(", unitOfMeasure=").append(unitOfMeasure).append(", packageDetails=")
			.append(packageDetails).append(", onuNumber=").append(onuNumber).append("]");
		return builder.toString();
	}
}

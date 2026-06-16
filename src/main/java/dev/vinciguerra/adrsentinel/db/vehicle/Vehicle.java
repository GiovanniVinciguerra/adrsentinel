package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entità JPA che rappresenta un Veicolo commerciale all'interno del dominio logistico ADR Sentinel.
 * <p>
 * Questa classe implementa un <b>Rich Domain Model</b>. Non è un semplice contenitore di dati, 
 * ma una vera e propria "cassaforte" che garantisce l'integrità fisica e normativa del veicolo 
 * attraverso la "Difesa in Profondità" (Defense in Depth):
 * </p>
 * <ul>
 * <li><b>Design dei Tipi (Wrapper vs Primitivi):</b> Le misurazioni fisiche utilizzano i wrapper (es. {@code Float}) 
 * in combinazione con {@code @NotNull} e {@code @Min e @Max} per distinguere esplicitamente l'assenza del dato 
 * dall'inserimento di un valore errato, migliorando la chiarezza delle API.</li>
 * <li><b>Fail-Safe (Principio del Minore Pericolo):</b> Per la certificazione ADR si utilizza il primitivo {@code boolean} 
 * di default a {@code false}. In caso di dato mancante, il sistema assume che il veicolo NON sia certificato per merci pericolose.</li>
 * <li><b>Domain-Driven Constraints:</b> I limiti, come il numero di assi (Max 8) e sulle dimensioni, sono calibrati rigorosamente 
 * sui mezzi standard reali del trasporto logistico italiano, escludendo a monte errori di battitura (Fat-Finger).</li>
 * </ul>
 * <li><b>Attenzione</b>: Il valori delle dimensioni (lunghezza, altezza, larghezza e passo) sono in metri. Per i pesi (peso complessivo 
 * a pieno carico e peso utile trasportabile) invece sono stati adottati i chilogrammi</li>
 * @author Giovanni Vinciguerra
 * @version 3.0 (Migliorata la gestione delle approvazioni adr)
 * @since 1.0
 */
@Entity
@Table(
	name = "vehicle",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_license_plate", columnNames = {"license_plate"})
	}
)
public class Vehicle {
	/**
	 * Value Object (Pattern @Embeddable) che incapsula la tipologia strutturale e di carico del veicolo.
	 * La sua validazione è innescata a cascata dall'annotazione {@code @Valid} nell'entità padre.
	 */
	@Embeddable
	public static class VehicleCategory  {
		public enum VehicleType {
			TANKER,
			CURTAINSIDE,
			REEFER,
			VAN,
			FLATBED,
			TIPPER,
			CHASSIS,
			ISOTANK
		}
		
		public enum LoadType {
			SOLID,
			LIQUID,
			GAS,
			SOLID_LIQUID,
			SOLID_GAS,
			LIQUID_GAS,
			ALL
		}
		
		public enum VehicleApproval {
			FL,
			AT,
			EX_II,
			EX_III,
			MEMU
		}
		
		@Enumerated(EnumType.STRING)
		@Column(
			name = "vehicle_type",
			nullable = false,
			length = 255
		)
		private VehicleType vehicleType;
		@Enumerated(EnumType.STRING)
        @Column(
        	name = "load_type",
        	nullable = false,
        	length = 255
        )
		private LoadType loadType;
		@ElementCollection(targetClass = VehicleApproval.class, fetch = FetchType.EAGER)
		@Enumerated(EnumType.STRING)
		@CollectionTable(
			name = "adr_vehicle_approval",
			joinColumns = @JoinColumn(name = "vehicle_id")
		)
		@Column(
			name = "vehicle_approval",
			nullable = false,
			length = 255
		)
		private Set<VehicleApproval> vehicleApprovals;

		public VehicleType getVehicleType() {
			return vehicleType;
		}
		
		public void setVehicleType(VehicleType vehicleType) {
			this.vehicleType = vehicleType;
		}
		
		public LoadType getLoadType() {
			return loadType;
		}
		
		public void setLoadType(LoadType loadType) {
			this.loadType = loadType;
		}
		
		public Set<VehicleApproval> getVehicleApprovals() {
			return vehicleApprovals;
		}
		
		public void setVehicleApprovals(Set<VehicleApproval> vehicleApprovals) {
			this.vehicleApprovals = vehicleApprovals;
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("VehicleCategory [vehicleType=").append(vehicleType).append(", loadType=")
				.append(loadType).append(", vehicleApprovals=").append(vehicleApprovals).append("]");
			return builder.toString();
		}
	}
	
	/** Chiave primaria surrogata autogenerata. */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Chiave di Business (Business Key) esposta e utilizzata per identificare logicamente il veicolo.
	 * <p>
	 * La validazione dei caratteri (lettere e numeri) è delegata a Java ({@code @Pattern}) per garantire 
	 * l'agnosticismo del database (poiché le regex DDL variano in base al vendor SQL). I vincoli strutturali 
	 * (univocità e lunghezza fissa massima) sono invece imposti al database.
	 * </p>
	 */
	@Column(
		name = "license_plate",
		unique = true,
		nullable = false,
		length = 10
	)
	private String licensePlate;
	/**
	 * Struttura del veicolo (Tipo di mezzo e stato fisico del carico supportato).
	 * {@code @Valid} assicura che il validatore ispezioni anche le annotazioni interne alla classe embeddata.
	 */
	@Embedded
	private VehicleCategory vehicleCategory;
	/** Peso massimo a pieno carico espresso in chilogrammi. */
	@Column(
		name = "max_weight_kg",
		nullable = false
	)
	private Integer maxWeightkg;
	/** Portata utile (peso massimo della merce trasportabile) espressa in chilogrammi. */
	@Column(
		name = "max_useful_weight_kg",
		nullable = false
	)
	private Integer maxUsefulWeightkg;
	/** Altezza massima del veicolo in metri (Critica per i percorsi con cavalcavia). */
	@Column(
		name = "height_m",
		nullable = false
	)
	private Float heightm;
	/** Larghezza del veicolo in metri. */
	@Column(
		name = "width_m",
		nullable = false
	)
	private Float widthm;
	/** Lunghezza complessiva del veicolo in metri. */
	@Column(
		name = "length_m",
		nullable = false
	)
	private Float lengthm;
	/** Interasse (Distanza tra l'asse anteriore e posteriore) in metri. */
	@Column(
		name = "wheelbase_m",
		nullable = false
	)
	private Float wheelbasem;
	/**
	 * Numero di assi fisici del veicolo.
	 * Limitato a 8 poiché il sistema gestisce trasporti commerciali standard e non veicoli eccezionali modulari.
	 */
	@Column(
		name = "n_axles",
		nullable = false
	)
	private Integer nAxles;
	/**
	 * Flag booleano utilizzato per la verifica e l'asseganzione del veicolo in fase di dispatch. Se {@code true} il veicolo non verrà 
	 * selezionato perchè già in viaggio.
	 */
	@Column(
		name = "is_in_transit",
		nullable = false
	)
	@ColumnDefault("false")
	private boolean inTransit;
	/**
	 * Flag booleano utilizzato per la soft-delete del veicolo. Se {@code true} il veicolo può essere selezionato 
	 * per le spedizioni, se  {@code false} il veicolo non verrà più selezionato, ma manterrà la consistenza relazionale del 
	 * database.
	 */
	@Column(
		name = "is_active",
		nullable = false
	)
	@ColumnDefault("true")
	private boolean active;
	
	/**
	 * Lifecycle Hook (Tolerant Reader) eseguito prima di interagire con il database.
	 * <p>
	 * Intercetta input formattati male dall'utente (es. " AB - 123 CD ") e li pulisce brutalmente 
	 * rimuovendo newline, tabulazioni, spazi intermedi e trattini, forzando l'uppercase. 
	 * Garantisce che nel database entri solo la stringa essenziale (es. "AB123CD").
	 * Inoltre verifica anche che il set associato alle approvazioni adr del veicolo non sia mai null ma al più empty.
	 * </p>
	 */
	@PrePersist
	@PreUpdate
	private void normalize() {
		if(licensePlate != null) {
			licensePlate = licensePlate.replaceAll("[\\r\\n\\t]+", " ");
			licensePlate = licensePlate.replaceAll("[\\s\\-]+", "");
			licensePlate = licensePlate.trim().toUpperCase();
		}
		if(vehicleCategory.vehicleApprovals == null)
			vehicleCategory.vehicleApprovals = new HashSet<VehicleCategory.VehicleApproval>();
	}
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getLicensePlate() {
		return licensePlate;
	}
	
	public void setLicensePlate(String licensePlate) {
		this.licensePlate = licensePlate;
	}
	
	public VehicleCategory getVehicleCategory() {
		return vehicleCategory;
	}
	
	public void setVehicleCategory(VehicleCategory vehicleCategory) {
		this.vehicleCategory = vehicleCategory;
	}
	
	public Integer getMaxWeightkg() {
		return maxWeightkg;
	}
	
	public void setMaxWeightkg(Integer maxWeightkg) {
		this.maxWeightkg = maxWeightkg;
	}
	
	public Integer getMaxUsefulWeightkg() {
		return maxUsefulWeightkg;
	}

	public void setMaxUsefulWeightkg(Integer maxUsefulWeightkg) {
		this.maxUsefulWeightkg = maxUsefulWeightkg;
	}

	public Float getHeightm() {
		return heightm;
	}
	
	public void setHeightm(Float heightm) {
		this.heightm = heightm;
	}
	
	public Float getWidthm() {
		return widthm;
	}
	
	public void setWidthm(Float widthm) {
		this.widthm = widthm;
	}
	
	public Float getLengthm() {
		return lengthm;
	}
	
	public void setLengthm(Float lengthm) {
		this.lengthm = lengthm;
	}
	
	public Float getWheelbasem() {
		return wheelbasem;
	}

	public void setWheelbasem(Float wheelbasem) {
		this.wheelbasem = wheelbasem;
	}

	public Integer getnAxles() {
		return nAxles;
	}

	public void setnAxles(Integer nAxles) {
		this.nAxles = nAxles;
	}
	
	public boolean isInTranit() {
		return inTransit;
	}
	
	public void setInTransit(boolean inTransit) {
		this.inTransit = inTransit;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	/** L'uguaglianza logica tra veicoli si basa esclusivamente sulla Business Key (Targa). */
	@Override
	public int hashCode() {
		return Objects.hash(licensePlate);
	}
	
	/**
	 * Verifica l'uguaglianza logica tra due istanze di {@code Vehicle}.
	 * <p>
	 * <b>Scelta Architetturale (Business Key Equality):</b><br>
	 * L'uguaglianza è basata <b>esclusivamente sulla targa ({@code licensePlate})</b>, che funge da chiave 
	 * di business naturale e immutabile, ignorando la chiave surrogata ({@code id}) e gli altri attributi.
	 * </p>
	 * <p>
	 * <b>Perché non usiamo l'ID?</b><br>
	 * Nelle entità JPA, l'ID viene generato dal database solo al momento del salvataggio (stato <i>Persistent</i>). 
	 * Se usassimo l'ID per l'uguaglianza, due veicoli appena creati in memoria (stato <i>Transient</i>) con ID {@code null} 
	 * verrebbero considerati uguali, o peggio, un veicolo cambierebbe il proprio hash code prima e dopo il salvataggio, 
	 * "scomparendo" se inserito in un {@link java.util.Set}.
	 * </p>
	 * @param obj l'oggetto con cui confrontare l'istanza corrente
	 * @return {@code true} se gli oggetti sono fisicamente la stessa istanza o se condividono la stessa targa; {@code false} altrimenti.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Vehicle other = (Vehicle) obj;
		return Objects.equals(licensePlate, other.licensePlate);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Vehicle [id=").append(id).append(", licensePlate=").append(licensePlate)
			.append(", vehicleCategory=").append(vehicleCategory).append(", maxWeightkg=").append(maxWeightkg)
			.append(", maxUsefulWeightkg=").append(maxUsefulWeightkg).append(", heightm=").append(heightm)
			.append(", widthm=").append(widthm).append(", lengthm=").append(lengthm).append(", wheelbasem=")
			.append(wheelbasem).append(", nAxles=").append(nAxles).append(", inTransit=").append(inTransit)
			.append(", active=").append(active) .append("]");
		return builder.toString();
	}
}

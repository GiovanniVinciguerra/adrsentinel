package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.Objects;
import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

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
 * @version 2.0 (Refactored with Jakarta Validation)
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
			FLATBED
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
		
		@NotNull(message = "Vehicle type cannot be null")
		@Enumerated(EnumType.STRING)
		@Column(
			name = "vehicle_type",
			nullable = false,
			length = 255
		)
		private VehicleType vehicleType;
		@NotNull(message = "Load type cannot be null")
		@Enumerated(EnumType.STRING)
        @Column(
        	name = "load_type",
        	nullable = false,
        	length = 255
        )
		private LoadType loadType;

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

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("VehicleCategory [vehicleType=").append(vehicleType).append(", loadType=")
				.append(loadType).append("]");
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
	@NotBlank(message = "License plate cannot be empty or blank")
	@Pattern(
		regexp = "^[A-Z0-9]{4,10}$",
		message = "License plate must be between 4 and 10 characters and contain only uppercase letters and numbers"
	)
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
	@Valid
	@NotNull(message = "Vehicle category information is mandatory")
	@Embedded
	private VehicleCategory vehicleCategory;
	/** Peso massimo a pieno carico espresso in chilogrammi. */
	@NotNull(message = "Max weight (kg) cannot be null")
	@Min(value = 1500, message = "")
	@Max(value = 44000, message = "")
	@Column(
		name = "max_weight_kg",
		nullable = false
	)
	private Integer maxWeightkg;
	/** Portata utile (peso massimo della merce trasportabile) espressa in chilogrammi. */
	@NotNull(message = "Max useful weight (kg) cannot be null")
	@Positive(message = "Max useful weight (kg) must be strictly positive")
	@Column(
		name = "max_useful_weight_kg",
		nullable = false
	)
	private Integer maxUsefulWeightkg;
	/** Altezza massima del veicolo in metri (Critica per i percorsi con cavalcavia). */
	@NotNull(message = "Height (m) cannot be null")
	@DecimalMin(value = "1.7", message = "The minimum allowed height is 1.7 meters")
	@DecimalMax(value = "4.3", message = "Height cannot exceed the maximum limit of 4.3 meters")
	@Column(
		name = "height_m",
		nullable = false
	)
	private Float heightm;
	/** Larghezza del veicolo in metri. */
	@NotNull(message = "Width (m) cannot be null")
	@DecimalMin(value = "1.5", message = "The minimum allowed width is 1.5 meters")
	@DecimalMax(value = "2.6", message = "Width cannot exceed the maximum limit of 2.6 meters")
	@Column(
		name = "width_m",
		nullable = false
	)
	private Float widthm;
	/** Lunghezza complessiva del veicolo in metri. */
	@NotNull(message = "Length (m) cannot be null")
	@DecimalMin(value = "3.4", message = "The minimum allowed length is 3.4 meters")
	@DecimalMax(value = "18.75", message = "Length cannot exceed the maximum limit of 18.75 meters")
	@Column(
		name = "length_m",
		nullable = false
	)
	private Float lengthm;
	/** Interasse (Distanza tra l'asse anteriore e posteriore) in metri. */
	@NotNull(message = "Wheelbase (m) cannot be null")
	@DecimalMin(value = "1.9", message = "The minimum allowed wheelbase is 1.9 meters")
	@DecimalMax(value = "7", message = "Wheelbase cannot exceed the maximum limit of 7 meters")
	@Column(
		name = "wheelbase_m",
		nullable = false
	)
	private Float wheelbasem;
	/**
	 * Numero di assi fisici del veicolo.
	 * Limitato a 8 poiché il sistema gestisce trasporti commerciali standard e non veicoli eccezionali modulari.
	 */
	@NotNull(message = "Number of axles cannot be null")
	@Min(value = 2, message = "Number of axles must be at least 2")
	@Max(value = 8, message = "Number of axles exceeds standard ADR transport limits (Max 8 axles)")
	@Column(
		name = "n_axles",
		nullable = false
	)
	private Integer nAxles;
	/**
	 * Indicatore di conformità al trasporto di Merci Pericolose (Accordo ADR).
	 * Utilizza il tipo primitivo per innescare un fallback sicuro (false) in caso di omissione del dato.
	 */
	@Column(
		name = "adr_certified",
		nullable = false
	)
	@ColumnDefault("false")
	private boolean adrCertified = false;
	
	/**
	 * Lifecycle Hook (Tolerant Reader) eseguito prima di interagire con il database e dopo il caricamento in memoria.
	 * <p>
	 * Intercetta input formattati male dall'utente (es. " AB - 123 CD ") e li pulisce brutalmente 
	 * rimuovendo newline, tabulazioni, spazi intermedi e trattini, forzando l'uppercase. 
	 * Garantisce che nel database entri ed esca solo la stringa essenziale (es. "AB123CD").
	 * </p>
	 */
	@PrePersist
	@PreUpdate
	@PostLoad
	private void normalize() {
		if(licensePlate != null) {
			licensePlate = licensePlate.replaceAll("[\\r\\n\\t]+", " ");
			licensePlate = licensePlate.replaceAll("[\\s\\-]+", "");
			licensePlate = licensePlate.trim().toUpperCase();
		}
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

	public boolean isAdrCertified() {
		return adrCertified;
	}
	
	public void setAdrCertified(boolean adrCertified) {
		this.adrCertified = adrCertified;
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
			.append(wheelbasem).append(", nAxles=").append(nAxles).append(", adrCertified=").append(adrCertified)
			.append("]");
		return builder.toString();
	}
}

package dev.vinciguerra.adrsentinel.db.driver;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entità JPA che rappresenta l'istantanea (snapshot) dei dati di un autista al momento
 * dell'associazione a un determinato viaggio (Spedizione).
 * <p>
 * Questa classe è progettata per scopi di storicizzazione e auditing: garantisce che le 
 * informazioni anagrafiche, le scadenze dei documenti e le abilitazioni (es. ADR) dell'autista 
 * rimangano immutabili e fotografate al momento della spedizione. In questo modo, eventuali 
 * modifiche future all'anagrafica centrale dell'autista non altereranno lo storico dei viaggi passati.
 * </p>
 * <p>
 * L'entità è mappata sulla tabella {@code driver_snapshot} e tutti i suoi attributi 
 * di business sono rigorosamente contrassegnati come non aggiornabili ({@code updatable = false}).
 * </p>
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation & UX Normalization)
 * @since 1.0
 */
@Entity
@Table(name = "driver_snapshot")
public class DriverSnapshot {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(
		name = "full_name_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private String fullNameSnap;
	@Column(
		name = "tax_code_snap",
		updatable = false,
		nullable = false,
		length = 16
	)
	private String taxCodeSnap;
	@Column(
		name = "phone_number_snap",
		updatable = false,
		nullable = false,
		length = 16
	)
	private String phoneNumberSnap;
	@Column(
		name = "license_number_snap",
		updatable = false,
		nullable = false,
		length = 20
	)
	private String licenseSnap;
	@Column(
		name = "license_expire_date_snap",
		updatable = false,
		nullable = false
	)
	private LocalDate licenseExpireDateSnap;
	@Column(
		name = "cqc_expire_date_snap",
		updatable = false,
		nullable = true
	)
	private LocalDate cqcExpireDateSnap;
	@Column(
		name = "driver_approval_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private String driverApprovalSnap;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "shipment_id",
		updatable = false,
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_driver_snap_shipment")
	)
	private Shipment shipment;
	
	protected DriverSnapshot() { /* Costruttore lasciato volutamente vuoto */ }
	
	/**
	 * Crea una nuova istantanea copiando i dati dall'anagrafica principale del conducente.
	 * <p>
	 * Questo costruttore si occupa di trasferire i dati anagrafici e le scadenze. 
	 * Inoltre, esegue un appiattimento (flattening) delle abilitazioni possedute (es. patentini ADR):
	 * se il conducente non ha abilitazioni viene assegnato il valore {@code "NONE"}, 
	 * altrimenti le abilitazioni vengono concatenate in una singola stringa separata da virgole,
	 * ordinata alfabeticamente per garantire consistenza.
	 * </p>
	 * <p>
	 * Questo costruttore non rappresenta il metodo corretto per creare lo snapshot degli autisti, 
	 * perchè non collega l'entità allo shipment cui fa riferimento. Questo costruttore è da considerarsi 
	 * come helper del metodo {@link DriverSnapshot#fromDrivers}
	 * </p>
	 * @param driver l'entità {@link Driver} da cui estrarre i dati da storicizzare.
	 * @throws IllegalArgumentException se l'oggetto driver fornito è {@code null}.
	 */
	protected DriverSnapshot(Driver driver) throws IllegalArgumentException {
		if(driver == null)
			throw new IllegalArgumentException("Unable to create driver snapshot. Driver is null.");
		this.fullNameSnap = driver.getFullName();
		this.taxCodeSnap = driver.getTaxCode();
		this.phoneNumberSnap = driver.getPhoneNumber();
		this.licenseSnap = driver.getLicense();
		this.licenseExpireDateSnap = driver.getLicenseExpireDate();
		this.cqcExpireDateSnap = driver.getCqcExpireDate();
		/* Appiattimento delle approvazioni ADR possedute dall'autista */
		if(driver.getDriverApprovals().isEmpty())
			this.driverApprovalSnap = "NONE";
		else {
			this.driverApprovalSnap = driver.getDriverApprovals()
				.stream()
				.map(Enum::name)
				.sorted()
				.collect(Collectors.joining(","));
		}
	}
	
	/**
	 * Metodo di utilità per generare un set di istantanee (DriverSnapshot) a partire
	 * dai conducenti attualmente associati a una determinata spedizione.
	 * <p>
	 * Il metodo estrae i conducenti dalla spedizione fornita, ne crea un'istantanea 
	 * invocando il costruttore dedicato e associa direttamente l'istanza della spedizione
	 * a ogni snapshot generato.
	 * </p>
	 * <p>
	 * Questo è il metodo da considerarsi assolutamente corretto per la creazione di istantanee di 
	 * autisti.
	 * </p>
	 * @param shipment la spedizione ({@link Shipment}) di cui estrarre i driver e a cui legare gli snapshot.
	 * @return un {@link Set} di {@link DriverSnapshot} pronti per la persistenza.
	 * @throws IllegalArgumentException se la spedizione è {@code null} o se la lista dei conducenti è nulla o vuota.
	 */
	public static Set<DriverSnapshot> fromDrivers(Shipment shipment) throws IllegalArgumentException {
		if(shipment == null)
			throw new IllegalArgumentException("Unable to create driver snapshot. Shipment is null.");
		Set<Driver> drivers = shipment.getDrivers();
		if(drivers == null || drivers.isEmpty())
			throw new IllegalArgumentException("Unable to create multiple driver snapshot. Drivers set is null or empty.");
		return drivers.stream()
			.map(driver -> {
				DriverSnapshot driverSnap = new DriverSnapshot(driver);
				driverSnap.shipment = shipment;
				return driverSnap;
			})
			.collect(Collectors.toSet());
	}

	public Long getId() {
		return id;
	}

	public String getFullNameSnap() {
		return fullNameSnap;
	}

	public String getTaxCodeSnap() {
		return taxCodeSnap;
	}

	public String getPhoneNumberSnap() {
		return phoneNumberSnap;
	}

	public String getLicenseSnap() {
		return licenseSnap;
	}

	public LocalDate getLicenseExpireDateSnap() {
		return licenseExpireDateSnap;
	}

	public LocalDate getCqcExpireDateSnap() {
		return cqcExpireDateSnap;
	}

	public String getDriverApprovalSnap() {
		return driverApprovalSnap;
	}

	public Shipment getShipment() {
		return shipment;
	}
	
	/**
	 * Calcola l'hash code dell'entità basandosi esclusivamente sul numero di patente storicizzato 
	 * ({@code licenseSnap}), considerato come chiave di business univoca per il conducente nel
	 * contesto di uno specifico snapshot.
	 * @return il valore hash calcolato.
	 */
	@Override
	public int hashCode() {
		return Objects.hash(licenseSnap);
	}
	
	/**
	 * Verifica l'uguaglianza logica tra questo snapshot e un altro oggetto.
	 * <p>
	 * Due istanze di {@code DriverSnapshot} sono considerate uguali se condividono 
	 * esattamente lo stesso numero di patente storicizzato ({@code licenseSnap}).
	 * </p>
	 * @param obj l'oggetto da confrontare con l'istanza corrente.
	 * @return {@code true} se gli oggetti sono logicamente uguali, {@code false} altrimenti.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DriverSnapshot other = (DriverSnapshot) obj;
		return Objects.equals(licenseSnap, other.licenseSnap);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("DriverSnapshot [id=").append(id).append(", fullNameSnap=").append(fullNameSnap)
			.append(", taxCodeSnap=").append(taxCodeSnap).append(", phoneNumberSnap=").append(phoneNumberSnap)
			.append(", licenseSnap=").append(licenseSnap).append(", licenseExpireDateSnap=")
			.append(licenseExpireDateSnap).append(", cqcExpireDateSnap=").append(cqcExpireDateSnap)
			.append(", driverApprovalSnap=").append(driverApprovalSnap).append("]");
		return builder.toString();
	}
}

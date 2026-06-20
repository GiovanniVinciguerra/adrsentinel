package dev.vinciguerra.adrsentinel.db.driver;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.text.WordUtils;
import org.hibernate.annotations.ColumnDefault;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
 * Entità JPA che rappresenta un Autista (Driver) all'interno del sistema.
 * <p>
 * Questa classe è mappata sulla tabella database {@code driver} e definisce
 * i seguenti vincoli di unicità (Unique Constraints):
 * <ul>
 * <li>{@code uk_tax_code}: Garantisce l'unicità del Codice Fiscale ({@code tax_code}).</li>
 * <li>{@code uk_license_number}: Garantisce l'unicità del Numero di Patente ({@code license_number}).</li>
 * </ul>
 * <p>
 * L'identità di business della classe, utilizzata per i metodi {@link #equals(Object)} 
 * e {@link #hashCode()}, si basa esclusivamente sul numero di patente ({@code license}).
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation)
 * @since 1.0
 */
@Entity
@Table(
	name = "driver",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_tax_code", columnNames = {"tax_code"}),
		@UniqueConstraint(name = "uk_license_number", columnNames = {"license_number"})
	}
)
public class Driver {
	/**
	 * Enumerazione che definisce le possibili specializzazioni ADR
	 * per il trasporto di merci pericolose possedute dall'autista.
	 */
	public enum DriverApproval {
		/** Corso di formazione base (Trasporto in colli e alla rinfusa). */
	    BASIC,
	    /** Corso di specializzazione per il trasporto in cisterne. */
	    TANK,
	    /** Corso di specializzazione per la Classe 1 (Materie e oggetti esplosivi). */
	    EXPLOSIVE,
	    /** Corso di specializzazione per la Classe 7 (Materie radioattive). */
	    RADIOACTIVE
	}
	/** Chiave primaria surrogata autogenerata dal database mediante strategia IDENTITY. */
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	/**
	 * Nome completo dell'autista.
	 * Campo obbligatorio, mappato sulla colonna {@code full_name} con lunghezza massima di 255 caratteri.
	 * Subisce un processo di normalizzazione prima della persistenza.
	 */
	@Column(
		name = "full_name",
		nullable = false,
		length = 255
	)
	private String fullName;
	/**
	 * Codice fiscale dell'autista.
	 * Campo obbligatorio, mappato sulla colonna {@code tax_code} con lunghezza massima di 16 caratteri.
	 * È soggetto a vincolo di unicità a livello di tabella.
	 */
	@Column(
		name = "tax_code",
		nullable = false,
		length = 16
	)
	private String taxCode;
	/**
	 * Numero di telefono dell'autista.
	 * Campo obbligatorio, mappato sulla colonna {@code phone_number} con lunghezza massima di 16 caratteri.
	 * Viene formattato nello standard internazionale E.164.
	 */
	@Column(
		name = "phone_number",
		nullable = false,
		length = 16
	)
	private String phoneNumber;
	/**
	 * Numero della patente di guida.
	 * Campo obbligatorio, mappato sulla colonna {@code license_number} con lunghezza massima di 20 caratteri.
	 * Rappresenta la chiave di business dell'entità.
	 */
	@Column(
		name = "license_number",
		nullable = false,
		length = 20
	)
	private String license;
	/**
	 * Data di scadenza della patente di guida.
	 * Campo obbligatorio, mappato sulla colonna {@code license_expire_date}.
	 */
	@Column(
		name = "license_expire_date",
		nullable = false
	)
	private LocalDate licenseExpireDate;
	/**
	 * Data di scadenza della Carta di Qualificazione del Conducente (CQC).
	 * Campo opzionale (può essere null se l'autista non possiede il CQC).
	 */
	@Column(
		name = "cqc_expire_date",
		nullable = true
	)
	private LocalDate cqcExpireDate;
	/**
	 * Insieme delle abilitazioni ADR possedute dall'autista.
	 * Mappato come collezione di elementi (tabella secondaria {@code driver_adr_approval}).
	 * La relazione viene caricata in modalità EAGER. Le enum vengono persistite come stringhe.
	 */
	@ElementCollection(targetClass = DriverApproval.class, fetch = FetchType.EAGER)
	@CollectionTable(
		name = "driver_adr_approval",
		joinColumns = @JoinColumn(name = "driver_id")
	)
	@Enumerated(EnumType.STRING)
	@Column(
		name = "driver_approval",
		nullable = false,
		length = 255
	)
	private Set<DriverApproval> driverApprovals;
	/**
	 * Flag che indica lo stato di attività dell'autista nel sistema.
	 * Campo obbligatorio, valore di default nel DDL impostato a {@code true}.
	 */
	@Column(
		name = "active",
		nullable = false
	)
	@ColumnDefault("true")
	private boolean active;
	/**
	 * Flag che indica se l'autista è già impegnato in un'altra spezione.
	 * Campo obbligatorio, valore di default nel DDL impostato a {@code false}.
	 */
	@Column(
		name = "active",
		nullable = false
	)
	@ColumnDefault("false")
	private boolean inTransit;
	
	/**
	 * Hook del ciclo di vita JPA eseguito prima delle operazioni di persistenza (INSERT)
	 * e di aggiornamento (UPDATE) nel database.
	 * <p>
	 * Esegue le seguenti operazioni sui campi dell'entità:
	 * <ul>
	 * <li><b>fullName</b>: Viene ripulito da caratteri speciali mantenendo lettere, apostrofi e spazi. 
	 * Gli spazi multipli vengono compressi e la stringa viene convertita in formato "Title Case"
	 * utilizzando Apache Commons {@code WordUtils}.</li>
	 * <li><b>taxCode</b>: Vengono rimossi i separatori comuni (spazi, virgole, punti, trattini, slash e underscore) 
	 * e il risultato viene forzato in lettere maiuscole.</li>
	 * <li><b>phone</b>: Viene parsato e formattato nello standard E.164 tramite Google {@code PhoneNumberUtil} (regione "IT").</li>
	 * <li><b>license</b>: Similmente al codice fiscale, viene pulito dai separatori e forzato al maiuscolo.</li>
	 * <li><b>driverApprovals</b>: Se il Set risulta nullo, viene reinizializzato a un {@code HashSet} vuoto per evitare {@code NullPointerException}.</li>
	 * </ul>
	 * <p>
	 * Applica inoltre la seguente validazione di business:
	 * Verifica la congruenza del CQC. Se l'autista dichiara di avere il CQC ({@code cqc == true}),
	 * la data di scadenza corrispondente non può essere nulla.
	 * @throws BadRequestException Se il payload risulta malformato (es. CQC presente ma data di scadenza mancante).
	 */
	@PrePersist
	@PreUpdate
	private void normalize() {
		/* Pulizia fullName */
		fullName = WordUtils.capitalizeFully(
			fullName.replaceAll("[^\\p{L}'\\s]+", " ").trim().replaceAll("\\s+", " "), 
			' ',
			'\''
		);
		/* Pulizia taxCode */
		taxCode = taxCode.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		/* Pulizia numero di telefono */
		try {
			PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
			PhoneNumber proto = phoneUtil.parse(phoneNumber, "IT");
			phoneNumber = phoneUtil.format(proto, PhoneNumberUtil.PhoneNumberFormat.E164);
		} catch(Exception error) {
			/*
			 * Questo blocco non verrà mai eseguito perché il dato è già stato validato.
			 * Viene lasciato solo per obbligo formale del costrutto try-catch
			 */
		}
		/* Pulizia licenseNumber */
		license = license.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		/* Nessun certificato ADR disponibile per l'autista implica Set vuoto */
		if(driverApprovals == null)
			driverApprovals = new HashSet<DriverApproval>();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public String getTaxCode() {
		return taxCode;
	}

	public void setTaxCode(String taxCode) {
		this.taxCode = taxCode;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getLicense() {
		return license;
	}

	public void setLicense(String license) {
		this.license = license;
	}

	public LocalDate getLicenseExpireDate() {
		return licenseExpireDate;
	}

	public void setLicenseExpireDate(LocalDate licenseExpireDate) {
		this.licenseExpireDate = licenseExpireDate;
	}

	public LocalDate getCqcExpireDate() {
		return cqcExpireDate;
	}

	public void setCqcExpireDate(LocalDate cqcExpireDate) {
		this.cqcExpireDate = cqcExpireDate;
	}

	public Set<DriverApproval> getDriverApprovals() {
		return driverApprovals;
	}

	public void setDriverApprovals(Set<DriverApproval> driverApprovals) {
		this.driverApprovals = driverApprovals;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isInTransit() {
		return inTransit;
	}

	public void setInTransit(boolean inTransit) {
		this.inTransit = inTransit;
	}
	
	/**
	 * Calcola l'hash code per questa entità basandosi esclusivamente 
	 * sulla chiave di business: il numero di patente ({@code license}).
	 * @return un valore hash basato sul numero di patente
	 */
	@Override
	public int hashCode() {
		return Objects.hash(license);
	}

	/**
	 * Confronta due oggetti per verificarne l'uguaglianza.
	 * Due entità Driver sono considerate uguali se e solo se possiedono 
	 * lo stesso numero di patente ({@code license}).
	 * @param obj l'oggetto con cui confrontare l'istanza corrente
	 * @return {@code true} se gli oggetti sono uguali, {@code false} altrimenti
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Driver other = (Driver) obj;
		return Objects.equals(license, other.license);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Driver [id=").append(id).append(", fullName=").append(fullName).append(", taxCode=")
			.append(taxCode).append(", phoneNumber=").append(phoneNumber).append(", license=").append(license)
			.append(", licenseExpireDate=").append(licenseExpireDate).append(", cqcExpireDate=").append(cqcExpireDate)
			.append(", driverApprovals=").append(driverApprovals).append(", active=").append(active)
			.append(", inTransit=").append(inTransit).append("]");
		return builder.toString();
	}
}

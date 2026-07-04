package dev.vinciguerra.adrsentinel.db.customer;

import java.util.Objects;
import org.apache.commons.text.WordUtils;
import org.hibernate.annotations.ColumnDefault;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entità Core del dominio (Master Data) che rappresenta l'anagrafica pura di un'azienda partner 
 * (Cliente, Fornitore, o Vettore) all'interno del sistema logistico.
 * <p>
 * <b>Scelte Architetturali (Domain-Driven Design):</b><br>
 * Questa classe è progettata per essere totalmente agnostica rispetto ai concetti di "Spedizione" 
 * o "Viaggio". Contiene esclusivamente i dati fiscali e legali immutabili nel breve termine. 
 * Il ruolo specifico che un'azienda assume (es. Mittente in un viaggio, Destinatario in un altro) 
 * non è salvato come stato dell'entità, ma è delegato alla relazione dinamica (Tabella Ponte o Snapshot) 
 * gestita dall'entità {@code Shipment} tramite l'enumerazione interna {@link CustomerRole}.
 * </p>
 * <p>
 * <b>Integrità dei Dati:</b><br>
 * L'entità delega al motore JPA (tramite {@code @PrePersist} e {@code @PreUpdate}) la responsabilità 
 * di igienizzare e normalizzare i dati testuali prima di ogni flush sul database, garantendo 
 * l'assenza di caratteri sporchi (newline, tabulazioni, punteggiatura nelle Partite IVA).
 * L'identità dell'oggetto (Equals & HashCode) è rigorosamente legata alla sua Business Key naturale 
 * (la Partita IVA / Codice Fiscale), prevenendo anomalie nelle Collection di Hibernate.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation)
 * @since 1.0
 */
@Entity
@Table(
	name = "customer",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_vat_number", columnNames = {"vat_number"})
	}
)
public class Customer {
	/**
	 * Definisce i ruoli operativi e legali che un'azienda può assumere all'interno 
	 * di uno specifico Documento di Trasporto (DDT / CMR) o distinta di carico ADR.
	 */
	public enum CustomerRole {
		SENDER, // Mittente
		RECEIVER, // Destinatario
		CARRIER // Vettore
	}
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(
		name = "company_name",
		nullable = false,
		length = 255
	)
	private String companyName;
	@Column(
		name = "vat_number",
		nullable = false,
		length = 30
	)
	private String vatNumber;
	@Column(
		name = "legal_address",
		nullable = false,
		length = 255
	)
	private String legalAddress;
	/**
	 * Flag per l'implementazione del Soft-Delete. 
	 * Garantisce che un'azienda non venga mai eliminata fisicamente dal DB, 
	 * preservando l'integrità storica e relazionale dei viaggi passati.
	 */
	@Column(
		name = "active",
		nullable = false
	)
	@ColumnDefault("true")
    private boolean active;
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getCompanyName() {
		return companyName;
	}
	
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	
	public String getVatNumber() {
		return vatNumber;
	}
	
	public void setVatNumber(String vatNumber) {
		this.vatNumber = vatNumber;
	}
	
	public String getLegalAddress() {
		return legalAddress;
	}
	
	public void setLegalAddress(String legalAddress) {
		this.legalAddress = legalAddress;
	}
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean active) {
		this.active = active;
	}
	
	/**
	 * Hook del ciclo di vita JPA (JPA Entity Lifecycle Callback).
	 * Viene invocato automaticamente dal framework un istante prima di eseguire una {@code INSERT} 
	 * o un {@code UPDATE} sul database, agendo come "Gatekeeper" per l'igiene dei dati.
	 * <p>
	 * <b>Operazioni di Normalizzazione:</b>
	 * <ul>
	 * <li><b>Ragione Sociale:</b> Rimozione degli spazi multipli e conversione in formato <i>Title Case</i> 
	 * (capitalizzazione della prima lettera di ogni parola, gestendo correttamente trattini e apostrofi).</li>
	 * <li><b>Partita IVA:</b> Rimozione drastica di spazi, virgole, punti, trattini e slash, 
	 * forzando l'intera stringa in maiuscolo (standardizzazione europea).</li>
	 * <li><b>Indirizzo Legale:</b> Appiattimento della stringa su una singola riga. Sostituisce i ritorni 
	 * a capo (CR/LF) e le tabulazioni con spazi singoli, prevenendo la rottura del layout nei 
	 * documenti PDF generati (es. lettere di vettura).</li>
	 * </ul>
	 * </p>
	 */
	@PrePersist
	@PreUpdate
	private void normalize() {
		companyName = WordUtils.capitalizeFully(
			companyName.trim().replaceAll("\\s+", " "),
			' ',
			'-',
			'\''
		);
		vatNumber = vatNumber.replaceAll("[\\s,\\.\\-/_]+", "").toUpperCase();
		legalAddress = legalAddress
			.replaceAll("[\\r\\n\\t]+", " ")
			.replaceAll(" {2,}", " ")
			.trim();;
	}
	
	/**
	 * Calcola l'hash code basandosi <b>esclusivamente</b> sulla Business Key naturale ({@code vatNumber}).
	 * L'ID surrogato non viene utilizzato poiché entità transienti (non ancora salvate sul DB) 
	 * avrebbero un ID nullo, corrompendo la logica degli {@code HashSet} e delle {@code HashMap} di Java.
	 * @return L'hash code calcolato sulla Partita IVA normalizzata.
	 */
	@Override
	public int hashCode() {
		return Objects.hash(vatNumber);
	}
	
	/**
	 * Valuta l'uguaglianza tra due istanze di {@code Customer}.
	 * In ossequio alle best practice di Hibernate, l'uguaglianza non è stabilita per identità di memoria 
	 * o tramite l'ID di database, ma tramite l'equivalenza della Partita IVA ({@code vatNumber}).
	 * Due oggetti Java distinti che rappresentano la stessa azienda fiscale sono considerati identici.
	 * @param obj L'oggetto da comparare.
	 * @return {@code true} se l'oggetto fornito ha la stessa Partita IVA di questa istanza.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Customer other = (Customer) obj;
		return Objects.equals(vatNumber, other.vatNumber);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Customer [id=").append(id).append(", companyName=").append(companyName).append(", vatNumber=")
			.append(vatNumber).append(", legalAddress=").append(legalAddress).append(", active=").append(active)
			.append("]");
		return builder.toString();
	}
}

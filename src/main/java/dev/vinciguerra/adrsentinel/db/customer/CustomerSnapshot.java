package dev.vinciguerra.adrsentinel.db.customer;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import dev.vinciguerra.adrsentinel.db.customer.Customer.CustomerRole;
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

/**
 * Entità JPA (Domain Entity) che modella il record storico e immutabile (Snapshot) di un Cliente associato a una spedizione.
 * <p><b>Ruolo Architetturale e Immutabilità del Manifest:</b></p>
 * Questa classe implementa il pattern "Snapshot". Il suo scopo è cristallizzare le informazioni anagrafiche (Ragione Sociale,
 * Partita IVA, Indirizzo) e il ruolo logistico (es. Mittente, Destinatario) di un {@link Customer} nell'esatto istante in cui
 * la {@link Shipment} correlata abbandona la fase di pianificazione ({@code PLANNED}).
 * Architetturalmente, garantisce che qualsiasi mutazione futura sul master record del cliente (es. cambio di sede legale o
 * di ragione sociale) non alteri retroattivamente i dati storici delle spedizioni già partite o concluse.
 * <p><b>Design "Append-Only" e Vincoli di Database:</b></p>
 * L'intera entità è progettata per essere rigorosamente in sola lettura post-inserimento. Tutte le colonne fisiche
 * (escluse le chiavi primarie generate) sono blindate dall'attributo {@code updatable = false}. Questo forza Hibernate a
 * trattare la tabella come un registro di log (Append-Only), garantendo la conformità e l'inalterabilità del dato ai fini
 * di auditing e tracciabilità logistica.
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation & UX Normalization)
 * @since 1.0
 */
@Entity
@Table(name = "customer_snapshot")
public class CustomerSnapshot {
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(
		name = "company_name_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private String companyNameSnap;
	@Column(
		name = "vat_number_snap",
		updatable = false,
		nullable = false,
		length = 30
	)
	private String vatNumberSnap;
	@Column(
		name = "legal_address_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private String legalAddressSnap;
	@Enumerated(EnumType.STRING)
	@Column(
		name = "role_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private CustomerRole roleSnap;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "shipment_id",
		updatable = false,
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_customer_snap_shipment")
	)
	private Shipment shipment;
	
	/**
	 * Costruttore protetto di default (No-Args) richiesto specificamente dalle direttive JPA/Hibernate per
	 * l'instanziazione dell'entità tramite Reflection e la creazione di Proxy a runtime.
	 * Non destinato all'uso diretto all'interno della logica di business.
	 */
	protected CustomerSnapshot() { /* Costruttore lasciato volutamente vuoto */ }
	
	/**
	 * Costruttore protetto utilizzato internamente dal factory method per generare un'istanza storicizzata.
	 * <p><b>Isolamento dal Master:</b></p>
	 * Durante l'istanziazione, i dati anagrafici vengono copiati per valore dall'entità originale {@link Customer}
	 * e congelati nei rispettivi campi con suffisso "Snap", recidendo il legame vitale con l'anagrafica mutabile.
	 * @param customer L'entità master sorgente da cui estrarre i dati anagrafici.
	 * @param role Il ruolo logistico (es. SENDER, RECEIVER) che l'azienda ricopre per questa specifica spedizione.
	 * @throws IllegalArgumentException Se il cliente o il ruolo forniti risultano nulli, impedendo la creazione di record malformati.
	 */
	protected CustomerSnapshot(Customer customer, CustomerRole role) throws IllegalArgumentException {
		if(customer == null)
			throw new IllegalArgumentException("Unable to create customer snapshot. Customer is null.");
		else if(role == null)
			throw new IllegalArgumentException("Unable to create customer snapshot. Role is null.");
		this.companyNameSnap = customer.getCompanyName();
		this.vatNumberSnap = customer.getVatNumber();
		this.legalAddressSnap = customer.getLegalAddress();
		this.roleSnap = role;
	}
	
	/**
	 * Pattern "Static Factory Method" che orchestra la generazione massiva degli snapshot per tutti i clienti coinvolti in un viaggio.
	 * <p><b>Flusso di Mappatura e Vincolo Relazionale:</b></p>
	 * Il metodo riceve in ingresso la spedizione "master", estrae la mappa dei clienti associati (chiave: {@link CustomerRole},
	 * valore: {@link Customer}), itera sulle singole entry e istanzia un {@link CustomerSnapshot} per ciascuna di esse.
	 * Contestualmente, inietta in ogni snapshot il riferimento foreign-key ({@code shipment}) necessario a consolidare la
	 * dipendenza Many-to-One sul database relazionale.
	 * @param shipment L'entità Spedizione di cui si intendono cristallizzare gli attori logistici.
	 * @return Un {@link Set} di snapshot popolati, mutuamente esclusivi per ruolo e Partita IVA, pronti per la persistenza.
	 * @throws IllegalArgumentException Se la spedizione è nulla, o se non è presente alcuna mappa di clienti ad essa associata.
	 */
	public static Set<CustomerSnapshot> fromCustomers(Shipment shipment) throws IllegalArgumentException {
		if(shipment == null)
			throw new IllegalArgumentException("Unable to create customer snapshot. Shipment is null.");
		Map<CustomerRole, Customer> customers = shipment.getCustomers();
		if(customers == null || customers.isEmpty())
			throw new IllegalArgumentException("Unable to create multiple customer snapshot. Customers map is null or empty.");
		return customers.entrySet()
			.stream()
			.map(entry -> {
				CustomerSnapshot customerSnap = new CustomerSnapshot(entry.getValue(), entry.getKey());
				customerSnap.shipment = shipment;
				return customerSnap;
			})
			.collect(Collectors.toSet());
	}

	public Long getId() {
		return id;
	}

	public String getCompanyNameSnap() {
		return companyNameSnap;
	}

	public String getVatNumberSnap() {
		return vatNumberSnap;
	}

	public String getLegalAddressSnap() {
		return legalAddressSnap;
	}

	public CustomerRole getRoleSnap() {
		return roleSnap;
	}

	public Shipment getShipment() {
		return shipment;
	}
	
	/**
	 * Calcola l'hash code dell'entità basandosi unicamente sulla Business Key storicizzata (Partita IVA).
	 * Questo approccio garantisce prestazioni ottimali e assenza di collisioni nell'inserimento in collezioni basate su Hash.
	 * @return Il valore hash derivato esclusivamente da {@code vatNumberSnap}.
	 */
	@Override
	public int hashCode() {
		return Objects.hash(vatNumberSnap);
	}
	
	/**
	 * Valuta l'uguaglianza tra due snapshot applicando il paradigma della "Business Key Equality".
	 * Due record storici sono considerati identici se condividono la stessa Partita IVA, ignorando la chiave surrogata (ID).
	 * @param obj L'oggetto target da confrontare.
	 * @return {@code true} se le entità condividono la medesima Partita IVA storicizzata, {@code false} altrimenti.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CustomerSnapshot other = (CustomerSnapshot) obj;
		return Objects.equals(vatNumberSnap, other.vatNumberSnap);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("CustomerSnapshot [id=").append(id).append(", companyNameSnap=").append(companyNameSnap)
			.append(", vatNumberSnap=").append(vatNumberSnap).append(", legalAddressSnap=").append(legalAddressSnap)
			.append(", roleSnap=").append(roleSnap).append("]");
		return builder.toString();
	}
}

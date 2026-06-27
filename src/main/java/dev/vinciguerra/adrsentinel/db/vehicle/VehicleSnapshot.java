package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.LoadType;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleType;
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
 * Entità che rappresenta la "Fotografia Legale" (Snapshot) inalterabile di un veicolo 
 * al momento dell'avvio di una spedizione (transizione allo stato {@code TRANSIT}).
 * <p><b>Contesto Architetturale e Immutabilità:</b></p>
 * Questa classe implementa il pattern architetturale di <i>Historical Record</i>. Il suo scopo 
 * è garantire che i dati fisici, normativi e di targa del veicolo rimangano cristallizzati, 
 * proteggendo l'integrità storica della spedizione da eventuali mutazioni future dell'anagrafica 
 * del veicolo master (es. cambio di omologazioni ADR).
 * L'immutabilità è rigorosamente forzata a due livelli:
 * <ul>
 * <li><b>Database Layer:</b> Utilizzo sistematico del parametro {@code updatable = false} su ogni colonna.</li>
 * <li><b>Application Layer:</b> Assenza totale di metodi <i>Setter</i>; l'entità è "Write-Once".</li>
 * </ul>
 * <p><b>Design Relazionale:</b></p>
 * Lo snapshot vive in funzione della spedizione. Il legame è definito da una relazione {@code @ManyToOne} 
 * con caricamento {@code EAGER}, giustificato dalla necessità di avere la fotografia del veicolo 
 * immediatamente disponibile ogni volta si ispezioni una spedizione in transito, completata o cancellata.
 * @author Giovanni Vinciguerra
 * @version 2.0 (Refactored with Jakarta Validation & UX Normalization)
 * @since 1.0
 */
@Entity
@Table(name = "vehicle_snapshot")
public class VehicleSnapshot {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(
		name = "license_plate_snap",
		updatable = false,
		nullable = false,
		length = 10
	)
	private String licensePlateSnap;
	@Enumerated(EnumType.STRING)
	@Column(
		name = "vehicle_type_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private VehicleType vehicleTypeSnap;
	@Enumerated(EnumType.STRING)
	@Column(
		name = "load_type_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private LoadType loadTypeSnap;
	/**
	 * Le approvazioni ADR vengono appiattite in una stringa (es. "AT,FL") per evitare 
	 * la creazione di tabelle relazionali aggiuntive (CollectionTable) legate allo snapshot.
	 * Questo garantisce che la riga dello Shipment sia auto-consistente e massimizza le performance di lettura.
	 */
	@Column(
		name = "vehicle_approvals_snap",
		updatable = false,
		nullable = false,
		length = 255
	)
	private String vehicleApprovalSnap;
	@Column(
		name = "max_weight_kg_snap",
		updatable = false,
		nullable = false
	)
	private int maxWeightkgSnap;
	@Column(
		name = "max_useful_weight_kg_snap",
		updatable = false,
		nullable = false
	)
	private int maxUsefulWeightkgSnap;
	@Column(
		name = "height_m_snap",
		updatable = false,
		nullable = false
	)
	private float heightmSnap;
	@Column(
		name = "width_m_snap",
		updatable = false,
		nullable = false
	)
	private float widthmSnap;
	@Column(
		name = "length_m_snap",
		updatable = false,
		nullable = false
	)
	private float lengthmSnap;
	@Column(
		name = "wheelbase_m_snap",
		updatable = false,
		nullable = false
	)
	private float wheelbasemSnap;
	@Column(
		name = "n_axles_snap",
		updatable = false,
		nullable = false
	)
	private int nAxleSnap;
	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(
		name = "shipment_id",
		updatable = false,
		nullable = false,
		foreignKey = @ForeignKey(name = "fk_vehicle_snap_shipment")
	)
	private Shipment shipment;
	
	/**
	 * Costruttore di default protetto richiesto dalle specifiche JPA (Hibernate).
	 * Viene lasciato volutamente vuoto e non deve essere utilizzato per l'istanziazione nel codice di business.
	 */
	protected VehicleSnapshot() { /* Costruttore lasciato volutamente vuoto */ }
	
	/**
	 * Costruisce uno snapshot completo estraendo e "congelando" i dati direttamente dall'entità {@link Shipment} 
	 * e dal suo {@link Vehicle} associato al momento dell'invocazione.
	 * <p><b>Logica di Appiattimento (Flattening):</b></p>
	 * Le approvazioni ADR ({@code VehicleApproval}), tipicamente modellate come collezione ({@code Set}), 
	 * vengono qui serializzate in una singola stringa testuale ordinata alfabeticamente e separata da virgole 
	 * (es. {@code "AT,FL"}), oppure valorizzate alla costante {@code "NONE"} se assenti.
	 * @param shipment La spedizione "padre" da cui estrarre il veicolo da fotografare. Non può essere nulla.
	 * @throws IllegalArgumentException Se la spedizione fornita è {@code null} o se alla spedizione 
	 * non è associato alcun veicolo al momento dello snapshot.
	 */
	public VehicleSnapshot(Shipment shipment) throws IllegalArgumentException {
		if(shipment == null)
			throw new IllegalArgumentException("Unable to create vehicle snapshot. Shipment is null.");
		Vehicle vehicle = shipment.getVehicle();
		if(vehicle == null)
			throw new IllegalArgumentException("Unable to create vehicle snapshot. Vehicle is null.");
		this.licensePlateSnap = vehicle.getLicensePlate();
		this.vehicleTypeSnap = vehicle.getVehicleCategory().getVehicleType();
		this.loadTypeSnap = vehicle.getVehicleCategory().getLoadType();
		Set<VehicleApproval> approvals = vehicle.getVehicleCategory().getVehicleApprovals();
		/* Appiattimento delle aprovazioni ADR possedute dal veicolo */
		if(approvals.isEmpty()) {
			this.vehicleApprovalSnap = "NONE";
		} else {
			this.vehicleApprovalSnap = approvals
				.stream()
				.map(Enum::name)
				.sorted()
				.collect(Collectors.joining(","));
		}
		this.maxWeightkgSnap = vehicle.getMaxWeightkg();
		this.maxUsefulWeightkgSnap = vehicle.getMaxUsefulWeightkg();
		this.heightmSnap = vehicle.getHeightm();
		this.widthmSnap = vehicle.getWidthm();
		this.lengthmSnap = vehicle.getLengthm();
		this.wheelbasemSnap = vehicle.getWheelbasem();
		this.nAxleSnap = vehicle.getnAxles();
		this.shipment = shipment;
	}

	public Long getId() {
		return id;
	}

	public String getLicensePlateSnap() {
		return licensePlateSnap;
	}

	public VehicleType getVehicleTypeSnap() {
		return vehicleTypeSnap;
	}

	public LoadType getLoadTypeSnap() {
		return loadTypeSnap;
	}

	public String getVehicleApprovalSnap() {
		return vehicleApprovalSnap;
	}

	public int getMaxWeightkgSnap() {
		return maxWeightkgSnap;
	}

	public int getMaxUsefulWeightkgSnap() {
		return maxUsefulWeightkgSnap;
	}

	public float getHeightmSnap() {
		return heightmSnap;
	}

	public float getWidthmSnap() {
		return widthmSnap;
	}

	public float getLengthmSnap() {
		return lengthmSnap;
	}

	public float getWheelbasemSnap() {
		return wheelbasemSnap;
	}

	public int getnAxleSnap() {
		return nAxleSnap;
	}

	public Shipment getShipment() {
		return shipment;
	}

	@Override
	public int hashCode() {
		return Objects.hash(licensePlateSnap);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VehicleSnapshot other = (VehicleSnapshot) obj;
		return Objects.equals(licensePlateSnap, other.licensePlateSnap);
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("VehicleSnapshot [id=").append(id).append(", licensePlateSnap=").append(licensePlateSnap)
			.append(", vehicleTypeSnap=").append(vehicleTypeSnap).append(", loadTypeSnap=").append(loadTypeSnap)
			.append(", vehicleApprovalSnap=").append(vehicleApprovalSnap).append(", maxWeightkgSnap=")
			.append(maxWeightkgSnap).append(", maxUsefulWeightkgSnap=").append(maxUsefulWeightkgSnap)
			.append(", heightmSnap=").append(heightmSnap).append(", widthmSnap=").append(widthmSnap)
			.append(", lengthmSnap=").append(lengthmSnap).append(", wheelbasemSnap=").append(wheelbasemSnap)
			.append(", nAxleSnap=").append(nAxleSnap).append("]");
		return builder.toString();
	}
}

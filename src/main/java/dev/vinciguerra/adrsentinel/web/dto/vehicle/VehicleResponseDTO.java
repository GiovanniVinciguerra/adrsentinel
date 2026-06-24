package dev.vinciguerra.adrsentinel.web.dto.vehicle;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshot;

/**
 * Data Transfer Object (DTO) in uscita (Response Payload) che rappresenta la scheda tecnica 
 * e normativa completa di un veicolo appartenente alla flotta aziendale.
 * <p><b>Contesto Architetturale (Immutabilità e Data Integration):</b></p>
 * Implementato come {@code record} Java, questo oggetto garantisce la massima efficienza nella 
 * serializzazione JSON e l'immutabilità dello stato durante il trasporto tra i layer. 
 * Agisce da <i>Aggregate DTO</i>, consolidando in un'unica vista coerente sia gli identificativi 
 * di business (Targa), sia il grafo delle omologazioni tecniche ({@link VehicleCategoryResponseDTO}), 
 * sia le metriche fisiche necessarie ai motori di calcolo esterni.
 * <p><b>Contesto di Dominio (Logistica Avanzata e Routing ADR):</b></p>
 * Questo payload è il pilastro informativo per due processi critici del sistema ADR Sentinel:
 * <ul>
 * <li><b>Load Balancing:</b> I campi relativi alle masse (Massa a pieno carico e Portata utile) 
 * permettono di verificare in tempo reale che il carico pericoloso assegnato non superi i limiti 
 * strutturali del mezzo e le soglie di esenzione ADR.</li>
 * <li><b>Precision Routing:</b> Le dimensioni (altezza, larghezza, lunghezza) e il numero di assi 
 * sono essenziali per gli algoritmi di navigazione professionale, al fine di evitare tratte con 
 * vincoli infrastrutturali (ponti, tunnel, strettoie) e calcolare correttamente i noli autostradali.</li>
 * </ul>
 * <p><b>Certificazione Legale:</b></p>
 * Il flag {@code adrCertified} rappresenta lo stato di omologazione legale del mezzo al trasporto 
 * di merci pericolose, fungendo da interruttore logico per l'abilitazione del veicolo all'interno 
 * delle procedure di spedizione ADR.
 * @param licensePlate La targa del veicolo, utilizzata come identificatore visivo e legale.
 * @param vehicleCategory Il DTO annidato contenente la classificazione tecnica ADR (es. FL, AT, OX).
 * @param maxWeightkg La massa massima tecnicamente ammissibile a pieno carico (in kg).
 * @param maxUsefulWeightkg La portata utile effettiva del mezzo (in kg), fondamentale per il calcolo del carico residuo.
 * @param heightm L'altezza massima del veicolo (in cm), critica per il transito in sottopassi e tunnel.
 * @param widthm La larghezza massima (in cm), utilizzata per il calcolo degli ingombri in tratte urbane o strette.
 * @param lengthm La lunghezza totale (in cm), determinante per le manovre e il posizionamento nei punti di carico.
 * @param wheelbasem Il passo del veicolo (distanza tra gli assi in cm), parametro tecnico per la stabilità e il raggio di sterzata.
 * @param nAxles Il numero totale di assi, variabile chiave per la ripartizione del peso e i costi di pedaggio.
 * @param adrCertified Indica se il veicolo è legalmente certificato e idoneo al trasporto ADR secondo la normativa vigente.
 * @param active Flag booleano che indica se il veicolo è attivo ({@code true}) oppure no ({@code false}). Può essere {@code null} nel caso in cui 
 * il veicolo rappresentato sia preso dallo storico {@link VehicleSnapshot}, in cui non è presente un campo {@code active}.
 * @param inTrnasit Flag booleano che indica se il conducente è attualmente impegnato in un viaggio.
 * @param historicalData Flag booleano che indica se il record restituito fa parte di un archivio storico (snapshot) 
 * piuttosto che rappresentare lo stato anagrafico corrente.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Output Payload)
 * @since 1.0
 */
public record VehicleResponseDTO(String licensePlate, VehicleCategoryResponseDTO vehicleCategory, Integer maxWeightkg,
		Integer maxUsefulWeightkg, Float heightm, Float widthm, Float lengthm, Float wheelbasem, Integer nAxles, Boolean active, Boolean inTransit,
		Boolean historicalData) {
	/**
	 * Factory Method statico per la conversione (Mapping) e l'aggregazione di un'entità 
	 * di dominio {@link Vehicle} nel suo corrispondente Data Transfer Object in uscita 
	 * {@link VehicleResponseDTO}.
	 * <p><b>Contesto di Dominio (Gestione Flotta e Vincoli Fisici):</b></p>
	 * Nel dominio della logistica ADR, l'anagrafica del veicolo è critica per gli algoritmi 
	 * di <i>Load Balancing</i> (calcolo del peso utile e dei volumi) e per il <i>Routing</i> 
	 * (evitare ponti bassi, strettoie o divieti in base agli assi). Questo metodo astrae 
	 * l'entità di database e confeziona un payload contenente esclusivamente i parametri 
	 * tecnici, dimensionali e legali necessari all'interfaccia utente o al motore di calcolo del client.
	 * <p><b>Design Pattern e Architettura del Mapping:</b></p>
	 * <ul>
	 * <li><b>Sicurezza ed Esecuzione Sicura (Guard Clause):</b> L'apertura del metodo con 
	 * {@code if(entity == null)} garantisce un approccio difensivo (<i>Null-Safety</i>), 
	 * essenziale per evitare {@code NullPointerException} nel caso di chiamate asincrone, 
	 * join di database a vuoto (Outer Join) o operazioni su stream di dati.</li>
	 * <li><b>Risoluzione Modulare del Grafo (Delegated Mapping):</b> Per estrarre i metadati di 
	 * omologazione, il metodo delega la responsabilità al factory specifico 
	 * {@link VehicleCategoryResponseDTO#fromEntity}. Questo previene la duplicazione del 
	 * codice (DRY) e garantisce che ogni modifica futura alla struttura della categoria 
	 * venga ereditata automaticamente da questo payload.</li>
	 * </ul>
	 * @param entity L'istanza dell'entità JPA recuperata dalla base dati, rappresentante un 
	 * mezzo di trasporto fisico appartenente alla flotta aziendale. Ammette valori {@code null}.
	 * @return Una nuova istanza immutabile (Record) di {@link VehicleResponseDTO} pronta per 
	 * la serializzazione JSON, oppure {@code null} se l'input fornito era assente.
	 */
	public static VehicleResponseDTO fromEntity(Vehicle entity) {
		if(entity == null)
			return null;
		
		return new VehicleResponseDTO(
			entity.getLicensePlate(),
			VehicleCategoryResponseDTO.fromEntity(entity.getVehicleCategory()),
			entity.getMaxWeightkg(),
			entity.getMaxUsefulWeightkg(),
			entity.getHeightm(),
			entity.getWidthm(),
			entity.getLengthm(),
			entity.getWheelbasem(),
			entity.getnAxles(),
			entity.isActive(),
			entity.isInTranit(),
			false
		);
	}
	
	/**
	 * Converte la "Fotografia Legale" (Snapshot) nel corrispondente Data Transfer Object (DTO) 
	 * destinato al layer di presentazione (Controller/API).
	 * <p><b>Ruolo Architetturale (Pattern DTO e Mapper):</b></p>
	 * Questo metodo statico funge da <i>Adapter</i> in fase di lettura. Il suo scopo è isolare 
	 * il client esterno dalle logiche di ottimizzazione del database (strutture appiattite e de-normalizzate), 
	 * restituendo una struttura dati gerarchica e ricca (identica a quella di un veicolo "live").
	 * <p><b>Processo di Re-Idratazione (Unflattening) e Ricostruzione:</b></p>
	 * Poiché il {@link VehicleSnapshot} storicizza le informazioni in forma piatta, il mapper 
	 * esegue un reverse-engineering del dato transiente:
	 * <ul>
	 * <li><b>Transient Category:</b> Viene istanziata "al volo" una {@link VehicleCategory} fittizia. 
	 * Questo trucco permette di incapsulare i dati di tipo e carico, abilitando il riuso pulito 
	 * del mapper annidato {@code VehicleCategoryResponseDTO.fromEntity()}.</li>
	 * <li><b>Deserializzazione ADR:</b> La stringa appiattita delle omologazioni (es. {@code "AT,FL"}) 
	 * viene splittata usando la virgola come delimitatore e i singoli token vengono mappati 
	 * dinamicamente (tramite {@code Enum.valueOf}) per ricostruire il {@code Set<VehicleApproval>}. 
	 * Il caso limite {@code "NONE"} viene intercettato elegantemente per restituire una collezione vuota.</li>
	 * </ul>
	 * @implNote Gli ultimi parametri passati al costruttore del {@link VehicleResponseDTO} 
	 * ({@code null, null, true}) riflettono la natura storica del dato: i valori {@code null} omettono 
	 * deliberatamente le associazioni volatili del veicolo originale (active e inTransit), 
	 * mentre il flag booleano historicalData indica al client che questo veicolo è uno snapshot.
	 * @param entity L'entità storica ({@link VehicleSnapshot}) estratta dal database o dalla cache.
	 * @return Il payload {@link VehicleResponseDTO} assemblato e pronto per la serializzazione (es. JSON), 
	 * oppure {@code null} in modo silente (Null-Safety) se l'entità in ingresso è nulla.
	 */
	public static VehicleResponseDTO fromEntity(VehicleSnapshot entity) {
		if(entity == null)
			return null;
		
		/* Costruzione della VehicleCategory dovuta al flattening */
		VehicleCategory category = new VehicleCategory();
		category.setLoadType(entity.getLoadTypeSnap());
		category.setVehicleType(entity.getVehicleTypeSnap());
		Set<VehicleApproval> approvals;
		if(entity.getVehicleApprovalSnap().equals("NONE"))
			approvals = new HashSet<VehicleApproval>();
		else {
			approvals = Arrays
				.stream(
					entity.getVehicleApprovalSnap()
					.split(",")
				)
				.map(VehicleApproval::valueOf)
				.collect(Collectors.toSet());
		}
		category.setVehicleApprovals(approvals);
		
		return new VehicleResponseDTO(
			entity.getLicensePlateSnap(),
			VehicleCategoryResponseDTO.fromEntity(category),
			entity.getMaxWeightkgSnap(),
			entity.getMaxUsefulWeightkgSnap(),
			entity.getHeightmSnap(),
			entity.getWidthmSnap(),
			entity.getLengthmSnap(),
			entity.getWheelbasemSnap(),
			entity.getnAxleSnap(),
			null,
			null,
			true
		);
	}
}

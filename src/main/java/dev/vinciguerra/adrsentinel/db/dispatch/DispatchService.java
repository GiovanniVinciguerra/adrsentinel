package dev.vinciguerra.adrsentinel.db.dispatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClass;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRule;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRuleService;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumberService;
import dev.vinciguerra.adrsentinel.db.onunumber.TransportMode;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PackingGroup;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.PhysicalState;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.LoadType;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleApproval;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle.VehicleCategory.VehicleType;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleService;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.adrdispatch.DispatchRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.adrdispatch.DispatchResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.adrdispatch.OnuItemRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.adrdispatch.VehicleDispatchResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.onunumber.OnuNumberResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.vehicle.VehicleResponseDTO;

/**
 * Servizio di Dominio principale (Core Domain Service) per l'ottimizzazione logistica 
 * e l'assegnazione dei trasporti ADR (Dangerous Goods Dispatcher).
 * <p>
 * Questa classe rappresenta il cuore pulsante dell'applicativo AdrSentinel.
 * Implementa un motore decisionale multi-fase che prende in carico una distinta di spedizione 
 * (lista di merci ONU) e produce un piano di carico sicuro ed economicamente ottimizzato.
 * </p>
 * <p>
 * Il motore opera attraverso 5 fasi architetturali:
 * <ol>
 * <li><b>Enrichment & Graph Building:</b> Recupero delle anagrafiche dal DB e costruzione di un grafo di compatibilità chimica.</li>
 * <li><b>Safe Clustering (Segregazione):</b> Partizionamento delle merci in gruppi di carico isolati basati sui vertici del grafo, garantendo che merci incompatibili (es. Classe 3 e Classe 8) non viaggino mai sullo stesso mezzo.</li>
 * <li><b>ADR Exemption Check:</b> Calcolo del punteggio ADR (Regola del Capitolo 1.1.3.6) per determinare l'esenzione dalle patenti speciali per i trasporti in colli inferiori ai 1000 punti.</li>
 * <li><b>Fleet Matchmaking & Resource Tracking:</b> Filtraggio dei veicoli idonei per struttura, stato fisico e omologazioni (FL, AT, EX), con tracciamento "in-place" delle risorse consumate per prevenire il Double-Booking sui cluster multipli.</li>
 * <li><b>Response Packaging:</b> Assemblaggio del piano di trasporto definitivo.</li>
 * </ol>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class DispatchService {
	/**
	 * Record di supporto interno (Value Object contestuale) utilizzato per aggregare 
	 * i dati di input (DTO) con i metadati anagrafici (Entity) e la modalità di trasporto,
	 * riducendo le query al database e passando un unico oggetto arricchito ai validatori.
	 */
	private record EnrichedOnuItem(OnuItemRequestDTO dto, OnuNumber entity, TransportMode mode) {}
	
	private final CompatibilityRuleService compatibilityRuleService;
	private final OnuNumberService onuNumberService;
	private final VehicleService vehicleService;
	/**
	 * Set immutabile contenente i codici ONU di Categoria di Trasporto 1 
	 * che richiedono un moltiplicatore normativo eccezionale (x50).
	 */
	private static final Set<String> SPECIAL_MULTIPLIER_ONU = Set.of("0081", "0082", "0084", "0241", "0331", "0332", "0482", "1005", "1053");
	/**
	 * Inietta i servizi necessari per l'accesso ai dati anagrafici e logistici.
	 * @param compatibilityRuleService Servizio per la lettura delle matrici di compatibilità tra classi ADR.
	 * @param onuNumberService Servizio per la validazione e il recupero dei numeri ONU tramite chiave naturale.
	 * @param vehicleService Servizio per l'interrogazione della flotta aziendale.
	 */
	public DispatchService(CompatibilityRuleService compatibilityRuleService, OnuNumberService onuNumberService, VehicleService vehicleService) {
		this.compatibilityRuleService = compatibilityRuleService;
		this.onuNumberService = onuNumberService;
		this.vehicleService = vehicleService;
	}
	
	/**
	 * Elabora una richiesta di spedizione generando un piano di trasporto ottimizzato.
	 * <p>
	 * Il metodo è transazionale in sola lettura ({@code readOnly = true}) per garantire 
	 * performance ottimali e prevenire dirty reads durante l'estrazione della flotta e delle regole.
	 * </p>
	 * @param dispatchRequestDTO Il payload contenente gli articoli da spedire (inclusi pesi, modi di trasporto e codici ONU).
	 * @return Un {@link DispatchResponseDTO} contenente la lista dei viaggi programmati (dispatches), ognuno assegnato a un veicolo univoco.
	 * @throws ResourceNotFoundException Se una merce non è censita, se il carico supera la capacità del mezzo più grande, 
	 * o se la flotta idonea si esaurisce prima di aver allocato tutti i cluster (Out of Resources).
	 */
	@Transactional(readOnly = true)
	public DispatchResponseDTO dispatcher(DispatchRequestDTO dispatchRequestDTO) throws ResourceNotFoundException {
		List<OnuItemRequestDTO> itemsToShip = dispatchRequestDTO.items();
		// FASE 1 Arrichimento dati e costruzione grafo
		List<EnrichedOnuItem> enrichedOnuItems = new ArrayList<EnrichedOnuItem>();
		Map<AdrClass, List<AdrClass>> compatibilityGraph = new HashMap<AdrClass, List<AdrClass>>();
		for(OnuItemRequestDTO item : itemsToShip) {
			OnuNumber onu = onuNumberService.getByOnuCodeAndPackingGroupAndName(
				item.onuCode(),
				Enum.valueOf(PackingGroup.class, item.packingGroup()),
				item.name()
			);
			enrichedOnuItems.add(new EnrichedOnuItem(item, onu, Enum.valueOf(TransportMode.class, item.transportMode())));
			AdrClass adrClassA = onu.getAdrClass();
			List<CompatibilityRule> compatibilityMap = compatibilityRuleService.getByAdrClassA(adrClassA.getClassCode());
			for(CompatibilityRule rule : compatibilityMap)
				compatibilityGraph.computeIfAbsent(adrClassA, value -> new ArrayList<AdrClass>()).add(rule.getAdrClassB());
		}
		// FASE 2 Clustering (Ripartizione del Carico)
		List<List<EnrichedOnuItem>> safeClusters = new ArrayList<List<EnrichedOnuItem>>();
		for(EnrichedOnuItem item : enrichedOnuItems) {
			boolean isOnuItemAssigned = false;
			for(List<EnrichedOnuItem> cluster : safeClusters) {
				if(isSafeToAdd(item, cluster, compatibilityGraph)) {
					cluster.add(item);
					isOnuItemAssigned = true;
					break;
				}
			}
			if(!isOnuItemAssigned) {
				List<EnrichedOnuItem> newCluster = new ArrayList<EnrichedOnuItem>();
				newCluster.add(item);
				safeClusters.add(newCluster);
			}
		}
		// FASE 3 e 4 Regola 1000 punti e Matchmaking Veicolo
		List<VehicleDispatchResponseDTO> dispatchResults = new ArrayList<VehicleDispatchResponseDTO>();
		Set<Vehicle> alreadyAssignedVehicles = new HashSet<Vehicle>();
		for(List<EnrichedOnuItem> cluster : safeClusters) {
			int clusterTotalWeight_kg = 0;
			float totalAdrPoints = 0.0f;
			List<OnuNumberResponseDTO> onuNumbersDTO = new ArrayList<OnuNumberResponseDTO>();
			for(EnrichedOnuItem item : cluster) {
				clusterTotalWeight_kg += item.dto().netWeightkg();
				totalAdrPoints += calculateAdrPoints(item.entity(), item.dto().netWeightkg());
				onuNumbersDTO.add(OnuNumberResponseDTO.fromEntity(item.entity()));
			}
			boolean isExempt = (totalAdrPoints <= 1000.0f);
			List<Vehicle> vehiclesToAssign = vehicleService.getByMaxUsefulWeight(clusterTotalWeight_kg);
			if(vehiclesToAssign == null || vehiclesToAssign.isEmpty())
				throw new ResourceNotFoundException("No vehicle found with payload capacity: " + clusterTotalWeight_kg);
			if(!alreadyAssignedVehicles.isEmpty()) {
				for(int i=0; i<vehiclesToAssign.size(); i++) {
					if(alreadyAssignedVehicles.contains(vehiclesToAssign.get(i))) {
						vehiclesToAssign.remove(i);
						i--;
					}
				}
			}
			if(vehiclesToAssign == null || vehiclesToAssign.isEmpty()) // Ricontrollo perchè la lista potrebbe essere vuota dopo il ciclo
				throw new ResourceNotFoundException("Insufficient fleet capacity for cluster: " + cluster);
			Vehicle selectedVehicle = selectBestMatchingVehicle(cluster, vehiclesToAssign, isExempt);
			if(selectedVehicle == null)
				throw new ResourceNotFoundException("No suitable vehicle found for payload.");
			dispatchResults.add(
				new VehicleDispatchResponseDTO(
					VehicleResponseDTO.fromEntity(selectedVehicle),
					onuNumbersDTO,
					clusterTotalWeight_kg,
					isExempt
				)
			);
			alreadyAssignedVehicles.add(selectedVehicle);
		}
		// FASE 5 Ritorno della response impacchettata
		return new DispatchResponseDTO(dispatchResults);
	}
	
	/**
	 * Verifica se un nuovo articolo ONU può essere inserito all'interno di un cluster di carico esistente
	 * valutando il grafo delle compatibilità ADR.
	 * @param newItem L'articolo da aggiungere.
	 * @param cluster Il raggruppamento di articoli già consolidato.
	 * @param compatibilityGraph Grafo delle compatibilità dove la chiave è la classe sorgente e la value è la lista di classi ammesse.
	 * @return {@code true} se la sostanza è compatibile con TUTTE le sostanze attualmente nel cluster, {@code false} altrimenti.
	 */
	private boolean isSafeToAdd(EnrichedOnuItem newItem, List<EnrichedOnuItem> cluster, Map<AdrClass, List<AdrClass>> compatibilityGraph) {
		AdrClass newAdrClass = newItem.entity().getAdrClass();
		for(EnrichedOnuItem existingOnuItem : cluster) {
			AdrClass existingAdrClass = existingOnuItem.entity().getAdrClass();
			if(newAdrClass.equals(existingAdrClass))
				continue;
			List<AdrClass> compatibleAdrClasses = compatibilityGraph.getOrDefault(newAdrClass, Collections.emptyList());
			if(!compatibleAdrClasses.contains(existingAdrClass))
				return false;
		}
		return true;
	}
	
	/**
	 * Calcola il punteggio ADR per un singolo articolo logistico ai fini dell'applicazione 
	 * dell'esenzione parziale per quantità trasportate per unità di trasporto (Regola dei 1000 punti).
	 * <p>
	 * L'algoritmo implementa rigorosamente la tabella del <b>Capitolo 1.1.3.6.4 dell'Accordo ADR</b>,
	 * associando a ciascuna Categoria di Trasporto il rispettivo fattore di moltiplicazione.
	 * </p>
	 * * <b>Regole di Moltiplicazione Applicate:</b>
	 * <ul>
	 * <li><b>Categoria 0:</b> Esenzione MAI consentita. Il metodo satura artificialmente il calcolo restituendo 9999, invalidando all'istante la soglia limite.</li>
	 * <li><b>Categoria 1:</b> Moltiplicatore <b>x50</b> per sostanze ad altissimo rischio (es. UN 1005 Ammoniaca anidra, esplosivi specifici), <b>x20</b> per tutte le altre.</li>
	 * <li><b>Categoria 2:</b> Moltiplicatore <b>x3</b> (es. gas infiammabili, corrosivi forti).</li>
	 * <li><b>Categoria 3:</b> Moltiplicatore <b>x1</b> (es. liquidi infiammabili standard, vernici).</li>
	 * <li><b>Categoria 4:</b> Moltiplicatore <b>x0</b>. La merce non concorre al conteggio dei punti (es. batterie scariche).</li>
	 * </ul>
	 * @param onu L'entità anagrafica {@link OnuNumber} che definisce la categoria di trasporto e il codice identificativo della sostanza.
	 * @param netWeight_kg La quantità netta della sostanza trasportata, espressa in chilogrammi o litri.
	 * @return I punti ADR calcolati per la quantità specificata. Se la merce appartiene alla categoria 0, restituisce 9999.
	 * @throws IllegalArgumentException Se il database contiene un valore di categoria di trasporto non previsto dalla norma (diverso da 0, 1, 2, 3, 4).
	 */
	private int calculateAdrPoints(OnuNumber onu, int netWeight_kg) throws IllegalArgumentException {
		int category = onu.getTransportCategory();
		if(category == 0)
			return 9999;
		int multiplier = switch(category) {
			case 1 -> SPECIAL_MULTIPLIER_ONU.contains(onu.getOnuCode()) ? 50 : 20;
			case 2 -> 3;
			case 3 -> 1;
			case 4 -> 0;
			default -> throw new IllegalArgumentException("Invalid transport category for ONU " + onu.getOnuCode() + ": " + category);
		};
		return multiplier * netWeight_kg;
	}
	
	/**
	 * Seleziona il veicolo più economico (minor portata utile) tra quelli conformi normativamente.
	 * @param cluster Il gruppo di merci da trasportare.
	 * @param vehiclesToAssign Lista dei veicoli scremati per peso netto e disponibilità.
	 * @param isExempt Flag che indica se l'intero cluster gode dell'esenzione 1000 punti.
	 * @return L'istanza del {@link Vehicle} ottimale, o {@code null} se nessuno supera le validazioni normative.
	 */
	private Vehicle selectBestMatchingVehicle(List<EnrichedOnuItem> cluster, List<Vehicle> vehiclesToAssign, boolean isExempt) {
		return vehiclesToAssign.stream()
			.filter(vehicle -> isVehicleCompliantForOnuItemsCluster(vehicle, cluster, isExempt))
			.min(Comparator.comparingInt(Vehicle::getMaxUsefulWeightkg))
			.orElse(null);
	}
	
	/**
	 * Verifica che un determinato veicolo sia normativamente e strutturalmente idoneo a trasportare 
	 * <b>tutte</b> le sostanze contenute all'interno del cluster.
	 * @param vehicle Il veicolo in esame.
	 * @param cluster Il gruppo di merci consolidate.
	 * @param isExempt Flag di esenzione generale del cluster.
	 * @return {@code true} se il veicolo supera il test per ogni singola merce, {@code false} alla prima non conformità.
	 */
	private boolean isVehicleCompliantForOnuItemsCluster(Vehicle vehicle, List<EnrichedOnuItem> cluster, boolean isExempt) {
		for(EnrichedOnuItem item : cluster) {
			if(!isVehicleCompliantForSingleItem(vehicle, item, isExempt))
				return false;
		}
		return true;
	}
	
	/**
	 * Motore Decisionale di Validazione (Validation Rules Engine).
	 * Incrocia le specifiche strutturali del mezzo con le direttive chimico-fisiche della merce.
	 * @param vehicleToCheck Il veicolo da testare.
	 * @param enrichedOnuItem Il dettaglio dell'articolo ONU.
	 * @param isExempt {@code true} se si applica l'esenzione ex Cap 1.1.3.6.
	 * @return {@code true} se il veicolo possiede la struttura e le certificazioni (FL/AT/EX) per il trasporto.
	 */
	private boolean isVehicleCompliantForSingleItem(Vehicle vehicleToCheck, EnrichedOnuItem enrichedOnuItem, boolean isExempt) {
		TransportMode mode = enrichedOnuItem.mode();
		VehicleType vType = vehicleToCheck.getVehicleCategory().getVehicleType();
		LoadType vLoadType = vehicleToCheck.getVehicleCategory().getLoadType();
		Set<VehicleApproval> vApprovals = vehicleToCheck.getVehicleCategory().getVehicleApprovals();
		if(mode == TransportMode.TANK && (vType != VehicleType.TANKER && vType != VehicleType.ISOTANK))
			return false;
		if (mode == TransportMode.BULK && vType != VehicleType.TIPPER)
	        return false;
		if (mode == TransportMode.PACKAGES && (vType == VehicleType.TANKER || vType == VehicleType.ISOTANK))
	        return false;
		PhysicalState physicalState = enrichedOnuItem.entity().getPhysicalState();
		if (!isLoadTypeCompatible(vLoadType, physicalState))
	        return false;
		AdrClass adrClass = enrichedOnuItem.entity().getAdrClass();
		if(isExempt && mode == TransportMode.PACKAGES)
			return true;
		if(adrClass.getClassCode().startsWith("1"))
			if (!vApprovals.contains(VehicleApproval.EX_II) && !vApprovals.contains(VehicleApproval.EX_III) && !vApprovals.contains(VehicleApproval.MEMU))
	            return false;
		if (mode == TransportMode.TANK) {
	       boolean isFlammable = false;
	       if(adrClass.getClassCode().equals("3"))
	    	   isFlammable = true;
	       else if(adrClass.getClassCode().equals("2")) {
	    	   if(Set.of("1965", "1049", "1011", "1978").contains(enrichedOnuItem.entity().getOnuCode()))
	    		   isFlammable = true;
	       }
	       if(isFlammable) {
	    	   if(!vApprovals.contains(VehicleApproval.FL))
	    		   return false;
	    	   else {
	    		   if(!vApprovals.contains(VehicleApproval.AT) && !vApprovals.contains(VehicleApproval.FL))
	    			   return false;
	    	   }
	       }
	    }
		return true;
	}
	
	/**
	 * Mappa la capacità di carico del veicolo (LoadType) allo stato fisico effettivo della materia trasportata.
	 * @param vLoadType La certificazione fisica del cassone/cisterna (es. SOLID_LIQUID).
	 * @param itemPhysicalState Lo stato intrinseco della materia (es. LIQUID).
	 * @return {@code true} se il mezzo è a tenuta adatta per lo stato della merce.
	 */
	private boolean isLoadTypeCompatible(LoadType vLoadType, PhysicalState itemPhysicalState) {
		if(vLoadType == LoadType.ALL)
			return true;
		return switch(itemPhysicalState.name()) {
			case "SOLID" -> vLoadType == LoadType.SOLID  || vLoadType == LoadType.SOLID_LIQUID || vLoadType == LoadType.SOLID_GAS;
			case "LIQUID" -> vLoadType == LoadType.LIQUID || vLoadType == LoadType.SOLID_LIQUID || vLoadType == LoadType.LIQUID_GAS;
			case "GAS" -> vLoadType == LoadType.GAS || vLoadType == LoadType.SOLID_GAS || vLoadType == LoadType.LIQUID_GAS;
			default -> false;
		};
	}
}

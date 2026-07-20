package dev.vinciguerra.adrsentinel.db.vehicle;

import java.util.Objects;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;

/**
 * Service Layer (Fascia di Dominio) responsabile dell'orchestrazione logica e transazionale 
 * dell'entità {@link VehicleSnapshot}.
 * <p><b>Contesto Architetturale e Caching:</b></p>
 * La classe estende {@code AbstractGenericService}, ereditandone le fondamenta architetturali 
 * per la gestione manuale della memoria RAM (Cache). Il Service agisce da mediatore (Facade) 
 * tra il layer di persistenza (Database) e l'infrastruttura di Caching (Caffeine), implementando 
 * una strategia di tipo <i>Cache-Aside</i> ibrida:
 * <ul>
 * <li><b>Lettura (Dichiarativa):</b> Demandata alle annotazioni di Spring ({@code @Cacheable}).</li>
 * <li><b>Scrittura (Programmatica):</b> Gestita tramite delegati transazionali di <i>Post-Commit</i>, 
 * assicurando che la cache venga allineata solo ed esclusivamente a fronte di un persistimento effettivo.</li>
 * </ul>
 * <p><b>Vincoli di Dominio:</b></p>
 * Poiché il {@link VehicleSnapshot} rappresenta un "Historical Record" immutabile, questo Service 
 * espone unicamente metodi per la creazione (Insert) e la consultazione (Read). L'aggiornamento (Update) 
 * è architetturalmente bandito.
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class VehicleSnapshotService extends AbstractGenericService {
	private final VehicleSnapshotRepository vehicleSnapshotRepository;
	
	/**
	 * Costruttore per l'iniezione delle dipendenze (Constructor Injection).
	 * Passa il {@code CacheManager} alla superclasse astratta per abilitare le utility di manipolazione della RAM.
	 * @param vehicleSnapshotRepository Il layer di accesso ai dati (DAO) per gli snapshot.
	 * @param cacheManager Il gestore centrale delle cache dell'applicazione (es. Caffeine).
	 */
	public VehicleSnapshotService(VehicleSnapshotRepository vehicleSnapshotRepository, CacheManager cacheManager) {
		super(Objects.requireNonNull(cacheManager, "cacheManager must be not null"));
		this.vehicleSnapshotRepository = Objects.requireNonNull(vehicleSnapshotRepository, "vehicleSnapshotRepository must not be null.");
	}
	
	/**
	 * Recupera la fotografia legale del veicolo associata a una specifica spedizione, 
	 * privilegiando la lettura ultra-veloce da memoria RAM.
	 * <p><b>Meccanismo di Cache-Aside:</b></p>
	 * L'annotazione {@code @Cacheable} intercetta la chiamata:
	 * <ol>
	 * <li><b>Cache Hit:</b> Se l'elemento è presente nella cache, viene restituito istantaneamente bypassando il metodo.</li>
	 * <li><b>Cache Miss:</b> Il metodo viene eseguito, viene effettuato l'accesso al Database (loggato via console), 
	 * e il risultato viene contestualmente inserito in RAM per le letture successive.</li>
	 * </ol>
	 * @param id L'identificativo primario (Primary Key) della spedizione di riferimento.
	 * @return L'istanza di {@link VehicleSnapshot} recuperata.
	 * @throws ResourceNotFoundException Se la fotografia legale non è presente né in cache né a database 
	 * per l'ID spedizione fornito.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.VEHICLE_SNAPSHOT_BY_SHIPMENT_ID_CACHE, key = "#id")
	public VehicleSnapshot getByShipmentId(Long id) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the VehicleSnapshot by Shipment.Id: {}", id);
		return vehicleSnapshotRepository.findByShipment_Id(id)
			.orElseThrow(() -> new ResourceNotFoundException("VehicleSnapshot not found for Shipment.Id: " + id));
	}
	
	/**
	 * Persiste in modo sicuro una nuova "Fotografia Legale" nel database, orchestrando l'allineamento 
	 * della Cache di lettura in un contesto rigidamente transazionale.
	 * <p><b>Gestione Transazionale e Prevenzione Dirty Cache:</b></p>
	 * Operando in un contesto {@code @Transactional}, l'operazione di salvataggio a DB (Hibernate) 
	 * e l'aggiornamento della cache RAM (Caffeine) sono slegati temporalmente. 
	 * L'inserimento in cache viene differito alla fase di <i>After-Commit</i> mediante la registrazione 
	 * nel {@link TransactionSynchronizationManager}. 
	 * Questo trucco architetturale garantisce che, in caso di Rollback della transazione sul DB 
	 * (es. per un constraint violation o un errore di rete), la RAM non venga "inquinata" con uno 
	 * snapshot fantasma (Dirty Write).
	 * @param newVehicleSnapshot L'istanza transiente dello snapshot da cristallizzare nel database.
	 * @return L'entità persistita (managed entity), comprensiva dell'ID autogenerato dal database.
	 */
	@Transactional
	public VehicleSnapshot save(VehicleSnapshot newVehicleSnapshot) {
		logger.info("[DataBase CALL] Saving new VehicleSnapshot with licensePlate: {}", newVehicleSnapshot.getLicensePlateSnap());
		VehicleSnapshot savedVehicleSnapshot = vehicleSnapshotRepository.save(newVehicleSnapshot);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedVehicleSnapshot); }
		});
		return savedVehicleSnapshot;
	}
	
	/**
	 * Metodo helper privato invocato esclusivamente dalla callback di <i>Post-Commit</i>.
	 * Esegue l'iniezione (Put) chirurgica del record appena salvato all'interno della cache, 
	 * popolandola in via preventiva (Warm-up) affinché le successive interrogazioni via 
	 * {@code getByShipmentId} risultino in un <i>Cache Hit</i> immediato.
	 * @param savedVehicleSnapshot L'entità consolidata sul DB, da inserire nella RAM.
	 */
	private void syncCacheAfterInsert(VehicleSnapshot savedVehicleSnapshot) {
		storeInCache(
			CaffeineCacheConfiguration.VEHICLE_SNAPSHOT_BY_SHIPMENT_ID_CACHE,
			savedVehicleSnapshot.getShipment().getId(),
			savedVehicleSnapshot,
			CacheOperation.SINGLE_RECORD
		);
	}
}

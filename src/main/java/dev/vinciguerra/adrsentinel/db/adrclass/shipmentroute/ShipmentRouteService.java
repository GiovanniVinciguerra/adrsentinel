package dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment.ShipmentStatus;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentService;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItemService;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.exception.AddressNotResolvableException;
import dev.vinciguerra.adrsentinel.exception.GeocodingApiException;
import dev.vinciguerra.adrsentinel.exception.IllegalShipmentStateException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.GeoCoordinateResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO.Options;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO.ProfileParams;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO.Restrictions;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteRequestDTO.ShipmentRouteDetailDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteUpdateDTO;

/**
 * Servizio di orchestrazione (Core Domain Service) deputato alla gestione del ciclo di vita 
 * e al calcolo avanzato delle rotte logistiche (Shipment Routes) per veicoli pesanti (HGV).
 * <p>
 * <b>Ruolo Architetturale (Facade / Orchestrator):</b><br>
 * Questa classe funge da "direttore d'orchestra" per il dominio del routing. Coordina molteplici 
 * sottosistemi: interroga il database per i vincoli ADR delle merci ({@link ShipmentItemService}), 
 * traduce gli indirizzi testuali in coordinate GPS tramite geocoding ({@link OrsGeocodingService}), 
 * e comunica con il motore cartografico esterno (OpenRouteService) gestendo l'Anti-Corruption Layer 
 * dei payload JSON.
 * </p>
 * <p>
 * <b>Estensione AbstractGenericService:</b><br>
 * Ereditando dalla classe astratta, il servizio ottiene nativamente l'accesso strutturato 
 * al {@link CacheManager}, permettendo una gestione centralizzata e sicura delle memorie temporanee 
 * (es. Caffeine o Redis) per ottimizzare le performance in lettura.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class ShipmentRouteService extends AbstractGenericService {
	private final ShipmentRouteRepository shipmentRouteRepository;
	private final ShipmentItemService shipmentItemService;
	private final ShipmentService shipmentService;
	private final OrsGeocodingService orsGeocodingService;
	private final RestClient restClient;
	private final String apiKey;
	
	/**
	 * Costruttore per la Dependency Injection e la configurazione del client HTTP.
	 * <p>
	 * <b>Inizializzazione del RestClient:</b><br>
	 * Utilizza la factory moderna {@link RestClient} (introdotta in Spring Framework 6.1) per 
	 * creare un client HTTP sincrono pre-configurato con la Base URL del provider. I valori 
	 * sensibili (API Key e URL) vengono iniettati in modo sicuro tramite property binding 
	 * (annotazione {@code @Value}), evitando hardcoding nel codice sorgente.
	 * </p>
	 * @param shipmentRouteRepository Repository JPA per la persistenza delle rotte.
	 * @param shipmentItemService Servizio per il recupero della distinta di carico (merci ADR).
	 * @param orsGeocodingService Servizio specializzato nella conversione Indirizzo -> Lat/Lng.
	 * @param baseUrl L'endpoint radice dell'API di OpenRouteService (iniettato da application.yml).
	 * @param apiKey La chiave crittografica di autenticazione per i servizi ORS.
	 * @param cacheManager Il manager globale delle cache passato alla superclasse.
	 */
	protected ShipmentRouteService(ShipmentRouteRepository shipmentRouteRepository, ShipmentItemService shipmentItemService, ShipmentService shipmentService,
			OrsGeocodingService orsGeocodingService, @Value("${ors.api.base-url:https://api.openrouteservice.org}") String baseUrl, 
			@Value("${ors.api.key}") String apiKey, CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentRouteRepository = shipmentRouteRepository;
		this.shipmentItemService = shipmentItemService;
		this.shipmentService = shipmentService;
		this.orsGeocodingService = orsGeocodingService;
		this.apiKey = apiKey;
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}
	
	/**
	 * Recupera una rotta precedentemente calcolata dal database sfruttando un livello di Cache in RAM.
	 * <p>
	 * <b>Ottimizzazione (Caching Layer):</b><br>
	 * Annotato con {@code @Cacheable}, questo metodo intercetta la chiamata: se l'UUID è già 
	 * presente nella regione di memoria specificata, restituisce l'oggetto dalla Heap senza 
	 * eseguire la query SQL, abbattendo la latenza e il carico sul database (I/O).
	 * </p>
	 * @param routeUUID L'identificatore pubblico di sicurezza (Anti-IDOR) della rotta.
	 * @return L'entità {@link ShipmentRoute} idratata dal database o dalla cache.
	 * @throws ResourceNotFoundException Se l'UUID fornito non corrisponde a nessuna rotta salvata.
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_ROUTE_BY_ROUTE_UUID_CACHE, key = "#routeUUID")
	public ShipmentRoute getByRouteUUID(String routeUUID) throws ResourceNotFoundException {
		logger.info("[DataBase CALL] Searching for the ShipmentRoute by routeUUID: {}", routeUUID);
		return shipmentRouteRepository.findByRouteUUID(routeUUID)
			.orElseThrow(() -> new ResourceNotFoundException("ShipmentRoute not found: " + routeUUID));
	}
	
	/**
	 * Recupera l'elenco sequenziale dei segmenti di viaggio (Tratte/Legs) per una determinata spedizione, 
	 * interponendo un layer di Caching L1 (Caffeine) ad altissime prestazioni per abbattere il carico sul database.
	 * <p>
	 * <b>Strategia di Caching (Spring AOP Proxy):</b><br>
	 * Il metodo è decorato con {@code @Cacheable}. Al momento dell'invocazione, il Proxy AOP di Spring 
	 * intercetta la chiamata prima che entri nel metodo:
	 * <ul>
	 * <li><b>Cache Hit:</b> Se il {@code shipmentTrackingNumber} (usato dinamicamente come chiave tramite SpEL {@code key = "#shipmentTrackingNumber"}) 
	 * è già presente nella cache {@code SHIPMENT_ROUTE_BY_SHIPMENT_CACHE}, il framework restituisce immediatamente 
	 * la lista in memoria (complessità temporale O(1)), bypassando completamente l'esecuzione del metodo.</li>
	 * <li><b>Cache Miss:</b> Se il dato è assente o espirato (TTL), il metodo viene effettivamente eseguito, 
	 * interroga il database tramite il Repository, restituisce il risultato e, contemporaneamente, 
	 * popola la cache per le chiamate future.</li>
	 * </ul>
	 * </p>
	 * <p>
	 * <b>Telemetria e Monitoraggio dell'Efficienza:</b><br>
	 * Il log {@code "[DataBase CALL]"} è strategicamente posizionato <i>all'interno</i> del corpo del metodo. 
	 * Grazie al funzionamento del Proxy AOP, questa riga verrà stampata in console <b>esclusivamente in caso di Cache Miss</b>. 
	 * Questo approccio fornisce una metrica vitale (Observability) per monitorare l'efficienza della configurazione 
	 * di Caffeine: se il log appare ripetutamente per lo stesso Tracking Number, significa che le policy di 
	 * eviction (scadenza o dimensione massima) della cache sono da tarare.
	 * </p>
	 * <p>
	 * <b>Sicurezza (Anti-IDOR):</b><br>
	 * Continua a delegare la ricerca al Tracking Number (UUID) invece che alla Primary Key del database, 
	 * schermando il layer di persistenza da tentativi di enumerazione.
	 * </p>
	 * @param shipmentTrackingNumber L'identificatore alfanumerico pubblico (UUID) della spedizione di cui recuperare le rotte.
	 * @return L'elenco ({@link List}) delle entità {@link ShipmentRoute} idratate dal database o dalla cache.
	 * Restituisce una lista vuota se nessuna rotta è associata al Tracking Number indicato (Nessun {@code null} restituito).
	 */
	@Cacheable(value = CaffeineCacheConfiguration.SHIPMENT_ROUTE_BY_SHIPMENT_CACHE, key = "#shipmentTrackingNumber")
	public List<ShipmentRoute> getByShipmentTrackingNumber(String shipmentTrackingNumber) {
		logger.info("[DataBase CALL] Searching for the ShipmentRoute by Shipment trackingNumber: {}", shipmentTrackingNumber);
		return shipmentRouteRepository.findByShipmentTrackingNumber(shipmentTrackingNumber);
	}
	
	/**
	 * Persiste in modalità batch una collezione di nuovi segmenti di rotta (Legs) nel database 
	 * e orchestra il successivo popolamento (Warm-up) delle cache applicative.
	 * <p>
	 * <b>Pattern di Coerenza (Transaction Synchronization):</b><br>
	 * Il metodo affronta il classico problema della "Race Condition" tra Cache e Database. 
	 * Invece di aggiornare la memoria RAM (Caffeine) immediatamente dopo ogni singola {@code INSERT}, 
	 * l'operazione di caching viene delegata al {@link TransactionSynchronizationManager}. 
	 * Sfruttando l'hook {@code afterCommit()}, il sistema garantisce matematicamente che la cache 
	 * verrà popolata <b>solo ed esclusivamente</b> se la transazione SQL va a buon fine. 
	 * Se il database dovesse eseguire un Rollback (es. per violazione di un constraint), la RAM 
	 * rimarrà intonsa, prevenendo il gravissimo fenomeno del <i>Cache Poisoning</i> (Dati fantasma in memoria).
	 * </p>
	 * <p>
	 * <b>Telemetria:</b><br>
	 * Estrae preventivamente tutti i Route UUID tramite Stream API per generare un log di audit 
	 * pulito e conciso prima dell'apertura formale delle operazioni di I/O.
	 * </p>
	 * @param newShipmentRoutes La lista delle entità {@link ShipmentRoute} transitorie (Transient) 
	 * appena calcolate dal motore di geolocalizzazione, pronte per essere persistite.
	 * @return La lista delle entità aggiornate e gestite dal Persistence Context di Hibernate, 
	 * complete di chiavi primarie generate dal database.
	 */
	@Transactional
	public List<ShipmentRoute> save(List<ShipmentRoute> newShipmentRoutes) {
		List<String> UUIDs = newShipmentRoutes.stream().map(ShipmentRoute::getRouteUUID).toList();
		logger.info("[DataBase CALL] Saving new ShipmentRoutes with routeUUIDs: {}", UUIDs);
		List<ShipmentRoute> savedShipmentRoutes = new ArrayList<ShipmentRoute>();
		for(ShipmentRoute route : newShipmentRoutes)
			savedShipmentRoutes.add(shipmentRouteRepository.save(route));
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterInsert(savedShipmentRoutes); }
		});
		return savedShipmentRoutes;
	}
	
	/**
	 * Esegue l'aggiornamento transazionale di uno specifico segmento di rotta logistica, 
	 * applicando i nuovi parametri vettoriali e gestendo, ove necessario, la propagazione 
	 * dei vincoli ADR (Tunnel Restriction) verso la spedizione padre.
	 * <p>
	 * <b>Architettura, Sicurezza e Domain-Driven Design (DDD):</b>
	 * <ul>
	 * <li><b>Anti-IDOR:</b> Il metodo viene esposto ai layer superiori (Controller) richiedendo come 
	 * parametro di ricerca il {@code routeUUID} (identificativo pubblico) anziché la chiave primaria, 
	 * blindando l'accesso ai dati.</li>
	 * <li><b>Delegazione di Dominio e Ottimizzazione:</b> Prima di aggiornare la restrizione ADR, 
	 * l'algoritmo verifica se il nuovo valore differisce da quello attuale. In caso affermativo, 
	 * evita la mutazione diretta dell'entità padre e delega l'operazione di business al {@code ShipmentService}. 
	 * Questo rispetta i confini di dominio (DDD) e previene query di scrittura o manipolazioni di cache superflue.</li>
	 * <li><b>Propagazione Transazionale e Cache Safety:</b> L'eventuale chiamata al Service esterno 
	 * si unisce alla transazione corrente. L'aggiornamento in memoria locale (Caffeine) è blindato 
	 * contro le <i>race conditions</i>: gli hook di {@link TransactionSynchronizationManager} garantiscono 
	 * che l'allineamento della cache per questa rotta avvenga <b>esclusivamente</b> a valle di un 
	 * {@code COMMIT} finale e confermato sul database.</li>
	 * <li><b>Dirty Checking e Salvataggio:</b> Anche se l'annotazione {@code @Transactional} applicherebbe 
	 * le modifiche in automatico a fine metodo, l'invocazione esplicita di {@code repository.save(route)} 
	 * è mantenuta per garantire che l'hook di sincronizzazione riceva l'istanza finale tracciata.</li>
	 * </ul>
	 * </p>
	 * @param routeUUID L'identificatore alfanumerico univoco della tratta da aggiornare.
	 * @param updateDto Il payload validato (Data Transfer Object) contenente le nuove coordinate, 
	 * la polilinea aggiornata, le metriche di viaggio e la potenziale nuova restrizione gallerie.
	 * @return L'entità {@link ShipmentRoute} aggiornata e sincronizzata col database, completa 
	 * della referenza (aggiornata, se modificata) allo {@link Shipment} padre.
	 * @throws ResourceNotFoundException Se l'UUID fornito non corrisponde ad alcun segmento esistente 
	 * (Interrompe la transazione in modalità Fail-Fast).
	 * @throws IllegalShipmentStateException Se lo Shipment collegato a questa rotta non è più nello stato PLANNED, 
	 * bloccando mutazioni su veicoli già in transito o consegnati.
	 */
	@Transactional
	public ShipmentRoute updateByRouteUUID(String routeUUID, ShipmentRouteUpdateDTO updateDto) throws ResourceNotFoundException, IllegalShipmentStateException {
		logger.info("[DataBase CALL] Updating ShipmentRoute details with routeUUID: {}", routeUUID);
		ShipmentRoute route = shipmentRouteRepository.findByRouteUUID(routeUUID)
			.orElseThrow(() -> new ResourceNotFoundException("ShipmentRoute not found: " + routeUUID));
		if(route.getShipment().getShipmentStatus() != ShipmentStatus.PLANNED)
			throw new IllegalShipmentStateException("Update denied: shipment is no longer in PLANNED status.");
		route.setOriginLat(updateDto.originLat());
		route.setOriginLng(updateDto.originLng());
		route.setDestLat(updateDto.destLat());
		route.setDestLng(updateDto.destLng());
		route.setDistanceKm(updateDto.distancekm());
		route.setEtaMinutes(updateDto.etaMins());
		route.setGeometry(updateDto.geometry());
		TunnelRestriction newTunnelRestriction = Enum.valueOf(TunnelRestriction.class, updateDto.tunnelRestriction());
		if(route.getShipment().getTunnelRestriction() != newTunnelRestriction) {
			shipmentService.updateTunnelRestrictionByTrackingNumber(newTunnelRestriction, route.getShipment().getTrackingNumber());
			/* NOTA SU HIBERNATE: L'istruzione route.getShipment().setTunnelRestriction(...) è volutamente omessa.
			 * Poiché operiamo nella stessa transazione (stesso Persistence Context / L1 Cache), l'entità Shipment 
			 * modificata all'interno del Service è esattamente la stessa istanza in memoria referenziata 
			 * qui da route.getShipment(). L'oggetto genitore risulta quindi già aggiornato per referenza.
			 */
		}
		ShipmentRoute updatedRoute = shipmentRouteRepository.save(route);
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() { syncCacheAfterUpdate(updatedRoute); }
		});
		return updatedRoute;
	}
	
	/**
	 * Callback privata (Hook) invocata dal gestore delle transazioni esclusivamente dopo 
	 * un commit SQL (INSERT) avvenuto con successo.
	 * <p>
	 * <b>Strategia di Inserimento (Multi-Index Cache Warming):</b><br>
	 * Per ogni nuova tratta salvata, il metodo interroga l'orchestratore di cache centralizzato 
	 * per popolare contemporaneamente due regioni di memoria distinte:
	 * <ol>
	 * <li>La cache per la ricerca del singolo segmento (Chiave: {@code Route UUID}).</li>
	 * <li>La cache per la ricerca dell'intero viaggio a tappe (Chiave: {@code Shipment Tracking Number}). 
	 * Su quest'ultima utilizza la strategia {@code LIST_RECORD} per accodare dinamicamente il nuovo 
	 * segmento alla collezione preesistente.</li>
	 * </ol>
	 * </p>
	 * @param savedShipmentRoutes La collezione delle rotte appena consolidate nel database.
	 */
	private void syncCacheAfterInsert(List<ShipmentRoute> savedShipmentRoutes) {
		for(ShipmentRoute savedRoute : savedShipmentRoutes) {
			storeInCache(
				CaffeineCacheConfiguration.SHIPMENT_ROUTE_BY_ROUTE_UUID_CACHE,
				savedRoute.getRouteUUID(),
				savedRoute,
				CacheOperation.SINGLE_RECORD
			);
			storeInCache(
				CaffeineCacheConfiguration.SHIPMENT_ROUTE_BY_SHIPMENT_CACHE,
				savedRoute.getShipment().getTrackingNumber(),
				savedRoute,
				CacheOperation.LIST_RECORD
			);
		}
	}
	
	/**
	 * Callback privata (Hook) invocata dal gestore delle transazioni esclusivamente dopo 
	 * un commit SQL (UPDATE) avvenuto con successo.
	 * <p>
	 * <b>Strategia di Aggiornamento Immutabile (Zero Key-Shift):</b><br>
	 * A differenza di altre entità dove le chiavi di raggruppamento possono cambiare nel tempo, 
	 * sia il {@code Route UUID} (identificativo del segmento) che il {@code Tracking Number} 
	 * (identificativo della spedizione madre) sono vincolati architetturalmente a essere immutabili.
	 * Di conseguenza, il metodo invoca la variante a 4 parametri di {@code storeInCache} (senza {@code oldKey}), 
	 * demandando all'orchestratore la semplice sovrascrittura in-place per il record singolo e il 
	 * pattern <i>Extract-Replace</i> all'interno della lista.
	 * </p>
	 * @param updatedRoute L'istanza della rotta appena modificata e consolidata nel database.
	 */
	private void syncCacheAfterUpdate(ShipmentRoute updatedRoute) {
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_ROUTE_BY_ROUTE_UUID_CACHE,
			updatedRoute.getRouteUUID(),
			updatedRoute,
			CacheOperation.SINGLE_RECORD
		);
		storeInCache(
			CaffeineCacheConfiguration.SHIPMENT_ROUTE_BY_SHIPMENT_CACHE,
			updatedRoute.getShipment().getTrackingNumber(),
			updatedRoute,
			CacheOperation.LIST_RECORD
		);
	}
	
	/**
	 * Calcola, orchestra e genera la rotta stradale multi-tappa ottimizzata per veicoli pesanti (HGV), 
	 * integrando i vincoli di transito per le merci pericolose (ADR) e la gestione delle gallerie.
	 * <p>
	 * <b>Logica di Business e Algoritmo Multi-Stop (Segment-Splitting):</b><br>
	 * Il metodo supera il vincolo del routing Punto-Punto (A-B) introducendo un'architettura a segmenti 
	 * sequenziali (Legs). Dato un punto di origine e una lista ordinata di destinazioni, l'algoritmo 
	 * scompone il viaggio in {@code N} tratte indipendenti. Per ciascuna tratta, interroga il motore 
	 * cartografico esterno applicando in modo uniforme la restrizione ADR più severa calcolata 
	 * sull'intera spedizione.
	 * </p>
	 * <p>
	 * <b>Flusso di Esecuzione Tecnico:</b>
	 * <ol>
	 * <li><b>Geocoding Preventivo (Fail-Fast):</b> Traduce l'indirizzo di partenza e, tramite un ciclo 
	 * for lineare, tutti gli indirizzi delle tappe intermedie/finali in coordinate geografiche 
	 * ({@link GeoCoordinateResponseDTO}). Se un solo indirizzo fallisce la geolocalizzazione, il processo 
	 * si interrompe immediatamente per preservare risorse.</li>
	 * <li><b>Valutazione ADR Centralizzata:</b> Invoca {@link #calculateMaxTunnelRestriction} per determinare 
	 * la classe di severità delle gallerie (es. B, D/E) valida per l'intero viaggio.</li>
	 * <li><b>Ciclo di Calcolo dei Segmenti (Look-Ahead Loop):</b> Calcola il numero totale di tratte 
	 * ({@code totalSegments = waypoints.size() - 1}). Tramite un indice incrementale, estrae la coppia 
	 * di coordinate correnti (indice {@code i} come origine e {@code i + 1} come destinazione).</li>
	 * <li><b>Integrazione REST:</b> Marshalla la richiesta tramite {@link #buildOrsRequest} e interroga l'endpoint 
	 * {@code /v2/directions/driving-hgv} di OpenRouteService tramite {@link RestClient}.</li>
	 * <li><b>Normalizzazione delle Metriche:</b> Elabora la risposta JSON (Unmarshalling fluente tramite Stream/Optional), 
	 * converte la distanza in chilometri e applica l'arrotondamento all'intero superiore per i minuti 
	 * di percorrenza tramite {@link Math#ceilDiv(int, int)}.</li>
	 * <li><b>Persistenza Temporanea e Mapping:</b> Alimenta una collezione di entità {@link ShipmentRoute} (una per segmento), 
	 * mantenendo il legame di Foreign Key con lo {@code Shipment} padre.</li>
	 * </ol>
	 * </p>
	 * <p>
	 * <b>Gestione della Variable Capture nelle Lambda:</b><br>
	 * Poiché il ciclo for incrementa la variabile locale {@code i}, quest'ultima non è considerata <i>effectively final</i> 
	 * e non può essere catturata dalle espressioni lambda interne (nel {@code .map()} e nell'{@code .orElseThrow()}). 
	 * Il metodo risolve questo vincolo allocando ad ogni iterazione una costante di blocco immutabile 
	 * {@code final int currentStage = i + 1}, garantendo la thread-safety e la conformità al compilatore Java.
	 * </p>
	 * @param shipment L'entità di dominio {@link Shipment} contenente l'indirizzo di partenza, 
	 * la lista ordinata delle tappe di destinazione e i dettagli strutturali del veicolo (assi, peso, sagoma).
	 * @return Un {@link ShipmentRouteResponseDTO} completo, idratato tramite la factory statica cumulativa, 
	 * contenente l'elenco ordinato dei segmenti calcolati (completi di polilinee e telemetria) e i dati dello shipment.
	 * @throws GeocodingApiException Se si verifica un errore fatale di comunicazione HTTP/REST con il provider 
	 * OpenRouteService (es. timeout, credenziali errate, servizio 503).
	 * @throws AddressNotResolvableException Se il provider esterno non riesce a determinare una rotta stradale 
	 * valida per un determinato segmento a causa dei vincoli fisici del mezzo 
	 * o delle restrizioni sul trasporto hazmat (strada sbarrata o senza alternative legali).
	 */
	public ShipmentRouteResponseDTO routing(Shipment shipment) throws GeocodingApiException {
		logger.info("HGV route orchestration start for Shipment Tracking: [{}]", shipment.getTrackingNumber());
		List<GeoCoordinateResponseDTO> waypoints = new ArrayList<GeoCoordinateResponseDTO>();
		logger.info("Geocoding origin address: [{}]", shipment.getOriginAddress());
		waypoints.add(orsGeocodingService.geocodeAddress(shipment.getOriginAddress()));
		for (int i = 0; i < shipment.getDestinationAddresses().size(); i++) {
			String stageAddress = shipment.getDestinationAddresses().get(i);
			logger.info("Geocoding destination stage {}/[{}]: [{}]", (i + 1), shipment.getDestinationAddresses().size(), stageAddress);
			waypoints.add(orsGeocodingService.geocodeAddress(stageAddress));
		}
		TunnelRestriction tunnelRestriction = calculateMaxTunnelRestriction(shipment);
		List<ShipmentRoute> routeStages = new ArrayList<ShipmentRoute>();
		try {
			int totalSegments = waypoints.size() - 1;
			for(int i=0; i<totalSegments; i++) {
				int currentStage = i + 1;
				GeoCoordinateResponseDTO currentOrigin = waypoints.get(i);
				GeoCoordinateResponseDTO currentDest = waypoints.get(i + 1);
				logger.info("Sending routing request to ORS for segment {}/{}...", (i + 1), totalSegments);
				OrsRouteRequestDTO orsRequestDTO = buildOrsRequest(currentOrigin, currentDest, shipment.getVehicle(), tunnelRestriction);
				OrsRouteResponseDTO response = restClient.post()
					.uri("/v2/directions/driving-hgv")
					.header("Authorization", apiKey)
					.header("Content-Type", "application/json; charset=utf-8")
					.body(orsRequestDTO)
					.retrieve()
					.body(OrsRouteResponseDTO.class);
				ShipmentRoute stageEntity = Optional.ofNullable(response)
					.filter(res -> res.routes() != null && !res.routes().isEmpty())
					.map(res -> res.routes().get(0))
					.map(route -> {
						float distancekm = route.summary().distance() / 1000.0f;
						int etaMinutes = Math.ceilDiv(route.summary().duration().intValue(), 60);
						logger.info(
							"Segment {}/{} calculated. Distance: {} km, ETA: {} min.",
							currentStage,
							totalSegments,
							String.format("%.2f", distancekm),
							etaMinutes
						);
						ShipmentRoute entity = new ShipmentRoute();
						entity.setOriginLat(currentOrigin.latitude());
						entity.setOriginLng(currentOrigin.longitude());
						entity.setDestLat(currentDest.latitude());
						entity.setDestLng(currentDest.longitude());
						entity.setDistanceKm(distancekm);
						entity.setEtaMinutes(etaMinutes);
						entity.setGeometry(route.geometry());
						entity.setShipment(shipment);
						return entity;
					})
					.orElseThrow(() -> new AddressNotResolvableException("Map API returned zero routes for segment " + currentStage));
				routeStages.add(stageEntity);
			}
			logger.info("Multi-stop orchestration completed successfully. Total processed stages: {}", routeStages.size());
			return ShipmentRouteResponseDTO.fromEntity(routeStages, tunnelRestriction);
		} catch (RestClientException error) {
			logger.error("Fatal communication error with ORS during route calculation: {}", error.getMessage());
			throw new GeocodingApiException("Navigation service temporarily unavailable.", error);
		}
	}
	
	/**
	 * Calcola il livello massimo di restrizione per le gallerie analizzando l'intera distinta di carico.
	 * <p>
	 * <b>Politica di Sicurezza (Fail-Safe Default / Zero Trust):</b><br>
	 * Se la lista degli item risulta vuota (nessun dato ADR trovato), il sistema non assume che il 
	 * veicolo sia sicuro, ma applica preventivamente la classe di restrizione {@code TunnelRestriction.B} 
	 * (la seconda più severa). Questa scelta architetturale previene l'instradamento illegale in galleria 
	 * in caso di disallineamenti del database o ritardi di sincronizzazione, privilegiando la sicurezza pubblica 
	 * rispetto all'efficienza del tracciato.
	 * </p>
	 * @param shipment La spedizione di cui recuperare le merci trasportate.
	 * @return L'Enum {@link TunnelRestriction} rappresentante il vincolo più stringente calcolato.
	 */
	private TunnelRestriction calculateMaxTunnelRestriction(Shipment shipment) {
		logger.info("Start ADR restrictions evaluation for shipment Tracking Number: [{}]", shipment != null ? shipment.getTrackingNumber() : "NULL");
		List<ShipmentItem> items = shipmentItemService.getByShipment(shipment.getTrackingNumber());
		TunnelRestriction maxTunnelRestriction = items.stream()
			.map(item -> item.getOnuNumber().getTunnelRestriction())
			.max(Comparator.comparingInt(TunnelRestriction::getSeverityWeight)).orElse(TunnelRestriction.B);
		logger.info("Restrictions calculation completed for Shipping [{}]. Calculated restriction: [{}]", shipment.getTrackingNumber(), maxTunnelRestriction);
		return maxTunnelRestriction;
	}
	
	/**
	 * Converte il modello di dominio interno in un Request DTO compatibile con OpenRouteService.
	 * <p>
	 * <b>Ingegneria del Trasporto Pesante:</b><br>
	 * Il metodo esegue calcoli fisici propedeutici al tracciamento HGV: converte la massa a terra 
	 * da chilogrammi a tonnellate metriche e calcola la ripartizione del peso sugli assi (Axle Load), 
	 * parametro critico richiesto dai navigatori per escludere ponti e cavalcavia a rischio cedimento strutturale.
	 * </p>
	 * <p>
	 * <b>Mappatura Hazmat (Merci Pericolose):</b><br>
	 * Traduce l'entità complessa {@link TunnelRestriction} in un semplice flag booleano ({@code true}/{@code false}), 
	 * informando il motore cartografico della presenza di materie infiammabili, tossiche o esplosive a bordo.
	 * </p>
	 * @param origin Coordinate GPS di partenza (strutturate come [Longitudine, Latitudine]).
	 * @param destination Coordinate GPS di arrivo (strutturate come [Longitudine, Latitudine]).
	 * @param vehicle L'automezzo assegnato, contenente i vincoli fisici e strutturali.
	 * @param tunnelRestriction Il vincolo gallerie ADR imposto al convoglio.
	 * @return Il DTO {@link OrsRouteRequestDTO} formattato e pronto per la serializzazione in JSON.
	 */
	private OrsRouteRequestDTO buildOrsRequest(GeoCoordinateResponseDTO origin, GeoCoordinateResponseDTO destination, Vehicle vehicle,
			TunnelRestriction tunnelRestriction) {
		List<List<Double>> coordinates = List.of(
			List.of(origin.longitude(), origin.latitude()), 
			List.of(destination.longitude(), destination.latitude())
		);
		Double weightTon = vehicle.getMaxWeightkg().doubleValue() / 1000.0;
		Double axleLoadTon = weightTon / vehicle.getnAxles().doubleValue();
		Restrictions restrictions = new Restrictions(
			weightTon,
			vehicle.getHeightm(),
			vehicle.getLengthm(),
			vehicle.getWidthm(),
			axleLoadTon,
			tunnelRestriction == TunnelRestriction.NONE ? 
				false : 
				true
		);
		ProfileParams profileParams = new ProfileParams(
			restrictions
		);
		return new OrsRouteRequestDTO(coordinates, new Options(profileParams));
	}
	
	/**
	 * Mapper infrastrutturale responsabile dell'idratazione (Hydration) dell'entità di dominio 
	 * JPA {@link ShipmentRoute} a partire dal payload di conferma inviato dal client.
	 * <p>
	 * <b>Contesto Architetturale (Fase 2: "Conferma"):</b><br>
	 * Questo metodo rappresenta il fulcro del pattern di Persistenza Differita. 
	 * Invece di delegare interamente al database l'identità del record, il metodo invoca 
	 * il costruttore specializzato iniettando il {@code routeUUID} originato in precedenza 
	 * dal motore cartografico. Questo garantisce l'assoluta immutabilità della <i>Business Key</i> 
	 * fin dall'istanziazione dell'oggetto.
	 * </p>
	 * <p>
	 * <b>Gestione del Persistence Context (The Owning Side):</b><br>
	 * Oltre a travasare le metriche spaziali e temporali, il metodo esegue un'operazione vitale per 
	 * l'integrità relazionale di Hibernate: l'istruzione {@code route.setShipment(shipment)}. 
	 * Essendo la tabella {@code shipment_route} il "Lato Proprietario" (Owning Side) della relazione, 
	 * questa dipendenza è l'unica fonte di verità per il framework. L'operazione garantisce 
	 * la corretta valorizzazione della Foreign Key ({@code shipment_id}) durante la futura 
	 * transazione di {@code INSERT}, prevenendo eccezioni di tipo {@code DataIntegrityViolationException}.
	 * </p>
	 * @param dto Il Data Transfer Object contenente i dettagli vettoriali e le metriche di viaggio 
	 * (latitudini, longitudini, geometria, ETA), già rigorosamente validati a livello di Controller 
	 * tramite le custom annotation (es. {@code @ValidatorLatitude}).
	 * @param shipment L'entità padre (precedentemente estratta dal DB) a cui agganciare questo 
	 * specifico segmento di rotta.
	 * @return Un'istanza transitoria (Transient) di {@link ShipmentRoute}, logicamente coerente 
	 * e pronta per essere passata al Repository per il salvataggio fisico su database.
	 */
	public ShipmentRoute mapToEntity(ShipmentRouteDetailDTO dto, Shipment shipment) {
		ShipmentRoute route = new ShipmentRoute(dto.routeUUID());
		route.setOriginLat(dto.originLat());
		route.setOriginLng(dto.originLng());
		route.setDestLat(dto.destLat());
		route.setDestLng(dto.destLng());
		route.setDistanceKm(dto.distancekm());
		route.setEtaMinutes(dto.etaMins());
		route.setGeometry(dto.geometry());
		route.setShipment(shipment);
		return route;
	}
}

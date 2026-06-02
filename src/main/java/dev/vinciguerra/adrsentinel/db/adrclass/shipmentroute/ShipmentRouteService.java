package dev.vinciguerra.adrsentinel.db.adrclass.shipmentroute;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import dev.vinciguerra.adrsentinel.db.AbstractGenericService;
import dev.vinciguerra.adrsentinel.db.CaffeineCacheConfiguration;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumber.TunnelRestriction;
import dev.vinciguerra.adrsentinel.db.shipment.Shipment;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItem;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItemService;
import dev.vinciguerra.adrsentinel.db.vehicle.Vehicle;
import dev.vinciguerra.adrsentinel.exception.AddressNotResolvableException;
import dev.vinciguerra.adrsentinel.exception.GeocodingApiException;
import dev.vinciguerra.adrsentinel.exception.ResourceNotFoundException;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.GeoCoordinateResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO.Options;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO.ProfileParams;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteRequestDTO.Restrictions;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsRouteResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.ShipmentRouteResponseDTO;

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
	protected ShipmentRouteService(ShipmentRouteRepository shipmentRouteRepository, ShipmentItemService shipmentItemService,
			OrsGeocodingService orsGeocodingService, @Value("${ors.api.base-url:https://api.openrouteservice.org}") String baseUrl, 
			@Value("${ors.api.key}") String apiKey, CacheManager cacheManager) {
		super(cacheManager);
		this.shipmentRouteRepository = shipmentRouteRepository;
		this.shipmentItemService = shipmentItemService;
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
	 * Motore principale di calcolo (Routing Engine) per la determinazione del percorso ottimale.
	 * <p>
	 * <b>Flusso di Orchestrazione:</b>
	 * <ol>
	 * <li><b>Geocoding Sequenziale:</b> Risolve asincronamente (tramite provider) gli indirizzi di origine e destinazione.</li>
	 * <li><b>Valutazione ADR:</b> Analizza la distinta di carico per determinare il divieto di transito in galleria più restrittivo.</li>
	 * <li><b>Marshalling:</b> Costruisce il payload JSON strutturato secondo le rigide gerarchie attese dall'API di OSR.</li>
	 * <li><b>Integrazione Esterna:</b> Esegue una chiamata REST POST per l'elaborazione vettoriale del tracciato.</li>
	 * <li><b>Elaborazione Risultati:</b> Converte i dati grezzi (metri e secondi) in metriche di business (Km e Minuti arrotondati) 
	 * tramite operazioni matematiche (es. {@link Math#ceilDiv}).</li>
	 * <li><b>Creazione Entità:</b> Idrata un nuovo oggetto di dominio {@link ShipmentRoute} con geometria e statistiche.</li>
	 * </ol>
	 * </p>
	 * @param shipment L'entità Spedizione contenente le informazioni spaziali e il veicolo assegnato.
	 * @return Il DTO di risposta popolato con la geometria (Polyline) e i dati di tracciamento.
	 * @throws GeocodingApiException In caso di errori di comunicazione HTTP o se l'API non trova percorsi (es. isola irraggiungibile).
	 */
	public ShipmentRouteResponseDTO routing(Shipment shipment) throws GeocodingApiException {
		logger.info("HGV route orchestration start for Shipment Tracking: [{}]", shipment.getTrackingNumber());
		logger.info("Geocoding addresses in progress...");
		GeoCoordinateResponseDTO origin = orsGeocodingService.geocodeAddress(shipment.getOriginAddress());
		GeoCoordinateResponseDTO destination = orsGeocodingService.geocodeAddress(shipment.getDestinationAddress());
		TunnelRestriction tunnelRestriction = calculateMaxTunnelRestriction(shipment);
		OrsRouteRequestDTO orsRequestDTO = buildOrsRequest(origin, destination, shipment.getVehicle(), tunnelRestriction);
		try {
			logger.info("Sending a routing request to the ORS engine (Profile: driving-hgv)...");
			OrsRouteResponseDTO response = restClient.post()
				.uri("/v2/directions/driving-hgv")
				.header("Authorization", apiKey)
				.header("Content-Type", "application/json; charset=utf-8")
				.body(orsRequestDTO)
				.retrieve()
				.body(OrsRouteResponseDTO.class);
			return Optional.ofNullable(response)
				.filter(res -> res.routes() != null && !res.routes().isEmpty())
				.map(res -> res.routes().get(0))
				.map(route -> {
					float distancekm = route.summary().distance() / 1000.0f;
					int etaMinutes = Math.ceilDiv(route.summary().duration().intValue(), 60);
					logger.info("Route calculation completed successfully. Distance: {} km, ETA: {} min.", String.format("%.2f", distancekm), etaMinutes);
					ShipmentRoute entity = new ShipmentRoute();
					entity.setOriginLat(origin.latitude());
					entity.setOriginLng(origin.longitude());
					entity.setDestLat(destination.latitude());
					entity.setDestLng(destination.longitude());
					entity.setDistanceKm(distancekm);
					entity.setEtaMinutes(etaMinutes);
					entity.setTunnelRestriction(tunnelRestriction);
					entity.setGeometry(route.geometry());
					entity.setShipment(shipment);
					logger.info("Shipment Route object creation completed. With route UUID: [{}]", entity.getRouteUUID());
					return ShipmentRouteResponseDTO.fromEntity(entity);
				})
				.orElseThrow(() -> new AddressNotResolvableException("Map API returned zero routes due to imposed restrictions."));
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
		logger.info("Restrictions calculation completed for Shipping [{}]. Restrizione calcolata: [{}]", shipment.getTrackingNumber(), maxTunnelRestriction);
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
}

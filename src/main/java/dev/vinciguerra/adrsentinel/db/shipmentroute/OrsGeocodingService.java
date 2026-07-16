package dev.vinciguerra.adrsentinel.db.shipmentroute;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import dev.vinciguerra.adrsentinel.exception.AddressNotResolvableException;
import dev.vinciguerra.adrsentinel.exception.GeocodingApiException;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.GeoCoordinateResponseDTO;
import dev.vinciguerra.adrsentinel.web.dto.shipmentroute.OrsGeocodeResponseDTO;

/**
 * Servizio infrastrutturale (Infrastructure Layer) responsabile dell'integrazione con 
 * il provider cartografico esterno OpenRouteService (ORS) per le operazioni di Geocoding.
 * <p>
 * <b>Ruolo Architetturale:</b><br>
 * Questa classe agisce come un <i>Adapter</i> verso le API esterne. Isola il nucleo 
 * dell'applicazione (Domain Layer) dalle complessità del protocollo HTTP e dalle strutture 
 * dati proprietarie (GeoJSON) del provider.
 * </p>
 * <p>
 * <b>Dettagli Implementativi:</b>
 * <ul>
 * <li><b>Modern HTTP Client:</b> Sfrutta {@link RestClient} (introdotto in Spring Boot 3.2) 
 * per garantire chiamate REST sincrone, fluide e type-safe.</li>
 * <li><b>Defensive Programming:</b> Implementa un parsing rigoroso e null-safe della risposta 
 * JSON per prevenire interruzioni di servizio dovute a payload esterni malformati.</li>
 * <li><b>Resource Optimization:</b> Limita attivamente la ricerca al singolo risultato più 
 * rilevante ({@code size=1}), minimizzando il traffico di rete e l'allocazione di memoria.</li>
 * </ul>
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 1.0
 */
@Service
public class OrsGeocodingService {
	private static final Logger logger = LoggerFactory.getLogger(OrsGeocodingService.class);
	
	private final RestClient restClient;
	private final String apiKey;
	
	/**
	 * Costruttore del servizio. Inizializza il client HTTP configurando l'URL di base 
	 * e prelevando le credenziali di sicurezza dall'ambiente.
	 * @param baseUrl L'endpoint radice dell'API di OpenRouteService. Viene iniettato dinamicamente 
	 * tramite il file di configurazione (es. application.yml). Prevede un valore di fallback 
	 * di default ({@code https://api.openrouteservice.org}) per garantire l'avvio del contesto 
	 * anche in assenza della property.
	 * @param apiKey Il token crittografico necessario per l'autenticazione presso il provider. 
	 * Deve essere mantenuto segreto e preferibilmente iniettato tramite variabili d'ambiente.
	 * * <br><br><b>Nota di Sicurezza (Audit):</b> Il logging dell'apiKey a livello INFO 
	 * è utile in ambiente di sviluppo (DEV), ma si raccomanda di mascherarlo in produzione 
	 * per prevenire l'esposizione di segreti nei file di log.
	 */
	public OrsGeocodingService(
			@Value("${ors.api.base-url:https://api.openrouteservice.org}") String baseUrl, 
			@Value("${ors.api.key}") String apiKey) {
		logger.info("Initializing ORS Service. BaseUrl: [{}], ApiKey: [{}]", baseUrl, apiKey);
		this.apiKey = apiKey;
		this.restClient = RestClient.builder().baseUrl(baseUrl).build();
	}
	
	/**
	 * Converte un indirizzo testuale (human-readable) nelle corrispondenti coordinate 
	 * geospaziali esatte (Latitudine e Longitudine).
	 * <p>
	 * <b>Flusso di esecuzione (Execution Flow):</b>
	 * <ol>
	 * <li>Invia una richiesta HTTP GET all'endpoint {@code /geocode/search}.</li>
	 * <li>Richiede esplicitamente un solo risultato ({@code size=1}) per estrarre la corrispondenza esatta.</li>
	 * <li>Esegue una pipeline funzionale (Java Optional) per navigare l'albero GeoJSON in totale sicurezza.</li>
	 * <li>Estrae l'array di coordinate assicurandosi di prevenire {@code NullPointerException} 
	 * o {@code IndexOutOfBoundsException}.</li>
	 * </ol>
	 * </p>
	 * @param address L'indirizzo fisico completo da tradurre (es. "Via Roma 1, Milano, Italia").
	 * @return Un oggetto immutabile {@link GeoCoordinateResponseDTO} contenente Latitudine e Longitudine 
	 * mappate correttamente e pronte per l'uso interno.
	 * @throws AddressNotResolvableException Eccezione di dominio sollevata se il provider restituisce 
	 * un HTTP 200 OK ma l'indirizzo non produce alcun risultato utile sulla mappa (es. indirizzo inesistente).
	 * @throws GeocodingApiException Eccezione tecnica sollevata in caso di guasti infrastrutturali, 
	 * indisponibilità del server remoto (HTTP 5xx), credenziali revocate (HTTP 401) o superamento 
	 * del limite di rate-limiting (HTTP 429).
	 */
	public GeoCoordinateResponseDTO geocodeAddress(String address) {
		logger.info("Starting geocoding for address: [{}]", address);
		try {
			OrsGeocodeResponseDTO response = restClient.get()
				.uri(
					uriBuilder -> uriBuilder
						.path("/geocode/search")
						.queryParam("api_key", apiKey)
						.queryParam("text", address)
						.queryParam("size", 1).build()
					)
				.retrieve()
				.body(OrsGeocodeResponseDTO.class);
			// Pipeline Funzionale e Defensive Parsing
			return Optional.ofNullable(response)
				// 1. Verifica presenza dell'array features
				.filter(res -> res.features() != null && !res.features().isEmpty())
				// 2. Estrae il miglior risultato (indice 0)
				.map(res -> res.features().get(0))
				// 3. Verifica l'integrità strutturale dell'oggetto geometry
				.filter(feat -> feat.geometry() != null && feat.geometry().coordinates() != null)
				// 4. Verifica che la coppia di coordinate sia matematicamente presente
				.filter(feat -> feat.geometry().coordinates().size() >= 2)
				// 5. Mappatura nel DTO interno
				.map(feat -> new GeoCoordinateResponseDTO(feat.geometry().getLatitude(), feat.geometry().getLongitude()))
				// 6. Fallback in caso di catena interrotta
				.orElseThrow(() -> new AddressNotResolvableException(address));
		} catch(RestClientException error) {
			logger.info("Infrastructure failure while geocoding address [{}]. Cause: {}", address, error.getMessage());
			throw new GeocodingApiException(error.getMessage(), error);
		}
	}
}

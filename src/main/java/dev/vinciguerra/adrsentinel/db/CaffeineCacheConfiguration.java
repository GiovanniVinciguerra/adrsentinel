package dev.vinciguerra.adrsentinel.db;

import java.util.Arrays;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.vinciguerra.adrsentinel.db.adrclass.AdrClassCacheSetting;
import dev.vinciguerra.adrsentinel.db.compatibilityrule.CompatibilityRuleCacheSetting;
import dev.vinciguerra.adrsentinel.db.customer.CustomerCacheSetting;
import dev.vinciguerra.adrsentinel.db.customer.CustomerSnapshotCacheSetting;
import dev.vinciguerra.adrsentinel.db.driver.DriverCacheSetting;
import dev.vinciguerra.adrsentinel.db.driver.DriverSnapshotCacheSetting;
import dev.vinciguerra.adrsentinel.db.onunumber.OnuNumberCacheSetting;
import dev.vinciguerra.adrsentinel.db.shipment.ShipmentCacheSetting;
import dev.vinciguerra.adrsentinel.db.shipmentitem.ShipmentItemCacheSetting;
import dev.vinciguerra.adrsentinel.db.shipmentroute.ShipmentRouteCacheSetting;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleCacheSetting;
import dev.vinciguerra.adrsentinel.db.vehicle.VehicleSnapshotCacheSetting;

/**
 * Torre di controllo e configurazione dell'infrastruttura di Caching L1 (Local Memory).
 * <p>
 * <b>Architettura e Ruolo:</b><br>
 * Questa classe definisce il "Motore di Memoria" dell'applicazione. Utilizza <b>Caffeine</b> 
 * (lo standard di mercato ad altissime prestazioni per la JVM) per gestire l'evizione dei dati 
 * e mantenere la latenza a livelli vicini allo zero (O(1)) su tutto il dominio logistico ADR.
 * </p>
 * <p>
 * <b>Integrazione Type-Safe (Twelve-Factor App):</b><br>
 * Tramite l'annotazione {@link EnableConfigurationProperties}, questa classe si disaccoppia 
 * dai numeri magici (Hardcoding). Inietta a runtime i record di configurazione specifici per 
 * ogni dominio (Catalogo ONU, Flotta, Spedizioni, ecc.), i quali contengono le policy di 
 * Capacity Planning lette in modo rigoroso e sicuro dal file {@code application.yml}.
 * </p>
 * @author Giovanni Vinciguerra
 * @version 1.0 (Caffeine Engine & Type-Safe Integration)
 * @since 3.0
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties({
	AdrClassCacheSetting.class,
	CompatibilityRuleCacheSetting.class,
	OnuNumberCacheSetting.class,
	ShipmentCacheSetting.class,
	ShipmentItemCacheSetting.class,
	VehicleCacheSetting.class,
	VehicleSnapshotCacheSetting.class,
	DriverCacheSetting.class,
	DriverSnapshotCacheSetting.class,
	CustomerCacheSetting.class,
	CustomerSnapshotCacheSetting.class,
	ShipmentRouteCacheSetting.class
})
public class CaffeineCacheConfiguration {
	/** Identificatore della regione di memoria per la ricerca di una macro-classe ADR (es. Classe 3). */
	public static final String ADR_CLASS_BY_CLASS_CODE_CACHE = "adr_class_by_class_code";
	/** Identificatore della regione di memoria dedicata alla conservazione in blocco di tutte le 9 Classi ADR. */
	public static final String ALL_ADR_CLASS_CACHE = "all_adr_class";
	/** Chiave statica globale per l'estrazione dalla cache dell'intero catalogo delle Classi ADR. */
	public static final String ALL_ADR_CLASS_KEY = "all_adr_class_key";
	/**
	 * Identificatore della regione di memoria per la Matrice di Segregazione. 
	 * Memorizza le regole di compatibilità di carico partendo da una classe sorgente (AdrClass A). 
	 */
	public static final String COMPATIBILITY_RULE_ADR_CLASS_A_CACHE = "compatibility_rule_adr_class_a";
	/** Identificatore della regione di memoria per la ricerca puntuale di una materia tramite il suo codice a 4 cifre e il codice imballaggio e il suo nome. */
	public static final String ONU_NUMBER_BY_ONU_CODE_AND_PACKING_GROUP_AND_NAME_CACHE = "onu_number_by_onu_code_and_packing_group_and_name";
	/** Identificatore della regione di memoria per la ricerca di una materia tramite il suo codice a 4 cifre. */
	public static final String ONU_NUMBER_BY_ONU_CODE_CACHE = "onu_number_by_onu_code";
	/** Identificatore della regione di memoria per il raggruppamento delle merci in base al grado di pericolo (es. 33). */
	public static final String ONU_NUMBER_BY_KEMLER_CODE_CACHE = "onu_number_by_kemler_code";
	/** Identificatore della regione di memoria per il raggruppamento delle merci appartenenti alla stessa macro-classe. */
	public static final String ONU_NUMBER_BY_ADR_CLASS_CACHE = "onu_number_by_adr_class";
	/** Identificatore della regione di memoria per l'esportazione massiva (Client-Side Caching) di tutto il catalogo. */
	public static final String ONU_NUMBER_ALL_CACHE = "onu_number_all";
	/** Chiave statica globale per l'estrazione dalla cache dell'intero catalogo mondiale dei Numeri ONU. */
	public static final String ONU_NUMBER_ALL_KEY = "all_onu_number_key";
	/** Identificatore univoco della regione dedicata al lookup puntuale delle spedizioni mediante numero di tracciamento. */
	public static final String SHIPMENT_BY_TRACKING_NUMBER_CACHE = "shipment_by_tracking_number";
	/** Identificatore univoco della regione dedicata al raggruppamento temporale (es. spedizioni per data). */
	public static final String SHIPMENT_BY_SHIPMENT_DATE_CACHE = "shipment_by_shipment_date";
	/** Identificatore univoco per la topologia di cache dedicata al recupero puntuale delle singole righe di carico. */
	public static final String SHIPMENT_ITEM_BY_ITEM_UUID_CACHE = "shipment_item_by_item_uuid";
	/** Identificatore univoco per la topologia di cache aggregata, dedicata al raggruppamento delle righe di carico per Shipment. */
	public static final String SHIPMENT_ITEM_BY_SHIPMENT_CACHE = "shipment_item_by_shipment";
	/** Identificatore della regione di memoria per la ricerca istantanea di un mezzo tramite la targa. */
	public static final String VEHICLE_BY_LICENSE_PLATE_CACHE = "vehicle_by_license_plate";
	/** Identificatore della regione di memoria per il raggruppamento di mezzi in base alla loro portata utile (Capacity). */
	public static final String VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE = "vehicle_by_max_useful_weight";
	/** Identificatore della regione di memoria per il raggruppamento globale dell'intera flotta aziendale. */
	public static final String ALL_VEHICLE_CACHE = "all_vehicle";
	/** Chiave statica globale per l'estrazione dalla cache dell'intera flotta veicoli. */
	public static final String ALL_VEHICLE_KEY = "all_vehicle_key";
	/** Identificatore della regione di memoria per il raggruppamento degli storici del veicolo associato a una data spedizione. */
	public static final String VEHICLE_SNAPSHOT_BY_SHIPMENT_ID_CACHE = "vehicle_snapshot_by_shipment_id";
	/** Identificatore della regione di memoria per il raggruppamento globale di tutti gli autisti associati per license. */
	public static final String DRIVER_BY_LICENSE_CACHE = "driver_by_license";
	/** Identificatore della regione di memoria per il raggruppamento globale di tutti gli autisti. */
	public static final String ALL_DRIVER_CACHE = "all_driver";
	/** Chiave statica globale per l'estrazione dalla cache di tutti gli autisti. */
	public static final String ALL_DRIVER_KEY = "all_driver_key";
	/** Identificatore della regione di memoria per il raggruppamento degli storici dell'autista associato a una data spedizione. */
	public static final String DRIVER_SNAPSHOT_BY_SHIPMENT_ID_CACHE = "driver_snapshot_by_shipment_id";
	/** Identificatore della regione di memoria per il raggruppamento dei clienti associati per vat number. */
	public static final String CUSTOMER_BY_VAT_NUMBER_CACHE = "customer_by_vat_number";
	/** Identificatore della regione di memoria per il raggruppamento dei clienti associati per company name. */
	public static final String CUSTOMER_BY_COMPANY_NAME_CACHE = "customer_by_company_name";
	/** Identificatore della regione di memoria per il raggruppamento di tutti i clienti. */
	public static final String ALL_CUSTOMER_CACHE = "all_customer";
	/** Chiave statica globale per l'estrazione dalla cache di tutti i clienti. */
	public static final String ALL_CUSTOMER_KEY = "all_customer_key";
	/** Identificatore della regione di memoria per il raggruppamento degli storici del cliente associato a una data spedizione. */
	public static final String CUSTOMER_SNAPSHOT_BY_SHIPMENT_ID_CACHE = "customer_snapshot_by_shipment_id";
	/** Identificatore della regione di memoria per il raggruppamento (UUID) globale dei percorsi stradali. */
	public static final String SHIPMENT_ROUTE_BY_ROUTE_UUID_CACHE = "shipment_route_by_route_uuid";
	/** Identificatore della regione di memoria per il raggruppamento (Shipment.trackingNumber) globale dei percorsi stradali. */
	public static final String SHIPMENT_ROUTE_BY_SHIPMENT_CACHE = "shipment_route_by_shipment_cache";
	
	/**
	 * Fabbrica e registra nel container di Spring il gestore centrale delle cache (CacheManager).
	 * <p>
	 * Questo metodo agisce da orchestratore: legge le policy dai record di configurazione 
	 * e istanzia le singole regioni di memoria isolandole in compartimenti stagni. Questo garantisce 
	 * che un picco di traffico sulle ricerche transazionali (es. Spedizioni) non causi lo svuotamento 
	 * accidentale della memoria dedicata ai cataloghi statici (es. Numeri ONU o Flotta).
	 * </p>
	 * @param adrClassSetting le property di capacity planning per le macro-classi ADR.
	 * @param compatibilityRuleCacheSetting le property di capacity planning per la matrice di segregazione.
	 * @param onuNumberCacheSetting le property di capacity planning per il catalogo delle merci pericolose.
	 * @param shipmentCacheSetting le property di capacity planning per i dati transazionali (viaggi).
	 * @param shipmentItemCacheSetting le property di capacity planning per gli articoli contenuti nelle spedizioni.
	 * @param vehicleCacheSetting le property di capacity planning per la flotta aziendale.
	 * @param vehicleSnapshotCacheSetting le property di capacity planning per i dati storicizzati dei veicoli.
	 * @param driverCacheSetting le property di capacity planning per l'anagrafica dei conducenti.
	 * @param driverSnapshotCacheSetting le property di capacity planning per i dati storicizzati dei conducenti.
	 * @param customerSnapshotCacheSetting le property di capacity planning per i dati storicizzati dei clienti.
	 * @param shipmentRouteCacheSetting le property di capacity planning per le rotte seguite dai veicoli.
	 * @return l'istanza di {@link CacheManager} pronta per essere utilizzata dai proxy di Spring.
	 */
	@Bean
	public CacheManager cacheManager(AdrClassCacheSetting adrClassSetting, CompatibilityRuleCacheSetting compatibilityRuleCacheSetting, 
			OnuNumberCacheSetting onuNumberCacheSetting, ShipmentCacheSetting shipmentCacheSetting, ShipmentItemCacheSetting shipmentItemCacheSetting,
			VehicleCacheSetting vehicleCacheSetting, VehicleSnapshotCacheSetting vehicleSnapshotCacheSetting, DriverCacheSetting driverCacheSetting,
			DriverSnapshotCacheSetting driverSnapshotCacheSetting, CustomerCacheSetting customerCacheSetting,
			CustomerSnapshotCacheSetting customerSnapshotCacheSetting, ShipmentRouteCacheSetting shipmentRouteCacheSetting) {
		SimpleCacheManager cacheManager = new SimpleCacheManager();
		CaffeineCache adrClassClassCodeCache = buildCache(
			ADR_CLASS_BY_CLASS_CODE_CACHE,
			adrClassSetting.classCode().maxSize()
		);
		CaffeineCache adrClassAllCache = buildCache(
			ALL_ADR_CLASS_CACHE,
			adrClassSetting.allAdr().maxSize()
		);
		CaffeineCache compatibilityRuleAdrClassCache = buildCache(
			COMPATIBILITY_RULE_ADR_CLASS_A_CACHE,
			compatibilityRuleCacheSetting.adrClassA().maxSize()
		);
		CaffeineCache onuNumberOnuCodeAndPackingGroupCacheAndName = buildCache(
			ONU_NUMBER_BY_ONU_CODE_AND_PACKING_GROUP_AND_NAME_CACHE,
			onuNumberCacheSetting.onuCodeAndPackingGroup().maxSize()
		);
		CaffeineCache onuNumberOnuCodeCache = buildCache(
			ONU_NUMBER_BY_ONU_CODE_CACHE,
			onuNumberCacheSetting.onuCode().maxSize()
		);
		CaffeineCache onuNumberKemlerCodeCache = buildCache(
			ONU_NUMBER_BY_KEMLER_CODE_CACHE,
			onuNumberCacheSetting.kemlerCode().maxSize()
		);
		CaffeineCache onuNumberAdrClassCache = buildCache(
			ONU_NUMBER_BY_ADR_CLASS_CACHE,
			onuNumberCacheSetting.adrClass().maxSize()
		);
		CaffeineCache onuNumberAllCache = buildCache(
			ONU_NUMBER_ALL_CACHE,
			onuNumberCacheSetting.allOnuNumber().maxSize()
		);
		CaffeineCache shipmentTrackingCache = buildCache(
			SHIPMENT_BY_TRACKING_NUMBER_CACHE,
			shipmentCacheSetting.tracking().maxSize()
		);
		CaffeineCache shipmentVehicleCache = buildCache(
			SHIPMENT_BY_SHIPMENT_DATE_CACHE,
			shipmentCacheSetting.period().maxSize()
		);
		CaffeineCache shipmentItemByItemUUIDCache = buildCache(
			SHIPMENT_ITEM_BY_ITEM_UUID_CACHE,
			shipmentItemCacheSetting.itemUUID().maxSize()
		);
		CaffeineCache shipmentItemByShipmentCache = buildCache(
			SHIPMENT_ITEM_BY_SHIPMENT_CACHE,
			shipmentItemCacheSetting.shipment().maxSize()
		);
		CaffeineCache vehicleLicensePlateCache = buildCache(
			VEHICLE_BY_LICENSE_PLATE_CACHE,
			vehicleCacheSetting.licensePlate().maxSize()
		);
		CaffeineCache vehicleMaxUsefulWeightCache = buildCache(
			VEHICLE_BY_MAX_USEFUL_WEIGHT_CACHE,
			vehicleCacheSetting.maxUsefulWeight().maxSize()
		);
		CaffeineCache vehicleAllCache = buildCache(
			ALL_VEHICLE_CACHE,
			vehicleCacheSetting.allVehicle().maxSize()
		);
		CaffeineCache vehicleSnapshotByShipmentIdCache = buildCache(
			VEHICLE_SNAPSHOT_BY_SHIPMENT_ID_CACHE,
			vehicleSnapshotCacheSetting.shipmentId().maxSize()
		);
		CaffeineCache driverByLicenseCache = buildCache(
			DRIVER_BY_LICENSE_CACHE,
			driverCacheSetting.license().maxSize()
		);
		CaffeineCache allDriverCache = buildCache(
			ALL_DRIVER_KEY,
			driverCacheSetting.allDriver().maxSize()
		);
		CaffeineCache driverSnapshotByShipmentIdCache = buildCache(
			DRIVER_SNAPSHOT_BY_SHIPMENT_ID_CACHE,
			driverSnapshotCacheSetting.shipmentId().maxSize()
		);
		CaffeineCache customerByVatNumberCache = buildCache(
			CUSTOMER_BY_VAT_NUMBER_CACHE,
			customerCacheSetting.vatNumber().maxSize()
		);
		CaffeineCache customerByCompanyNameCache = buildCache(
			CUSTOMER_BY_COMPANY_NAME_CACHE,
			customerCacheSetting.companyName().maxSize()
		);
		CaffeineCache allCustomerCache = buildCache(
			ALL_CUSTOMER_CACHE,
			customerCacheSetting.allCustomer().maxSize()
		);
		CaffeineCache customerSnapshotByShipmentIdCache = buildCache(
			CUSTOMER_SNAPSHOT_BY_SHIPMENT_ID_CACHE,
			customerSnapshotCacheSetting.shipmentId().maxSize()
		);
		CaffeineCache shipmentRouteByRouteUUIDCache = buildCache(
			SHIPMENT_ROUTE_BY_ROUTE_UUID_CACHE,
			shipmentRouteCacheSetting.routeUUID().maxSize()
		);
		CaffeineCache shipmentRouteByShipmentCache = buildCache(
			SHIPMENT_ROUTE_BY_SHIPMENT_CACHE,
			shipmentRouteCacheSetting.shipmentTrackingNumber().maxSize()
		);
		cacheManager.setCaches(
			Arrays.asList(
				adrClassClassCodeCache,
				adrClassAllCache,
				compatibilityRuleAdrClassCache,
				onuNumberOnuCodeAndPackingGroupCacheAndName,
				onuNumberOnuCodeCache,
				onuNumberKemlerCodeCache,
				onuNumberAdrClassCache,
				onuNumberAllCache,
				shipmentTrackingCache,
				shipmentVehicleCache,
				shipmentItemByItemUUIDCache,
				shipmentItemByShipmentCache,
				vehicleLicensePlateCache,
				vehicleMaxUsefulWeightCache,
				vehicleAllCache,
				vehicleSnapshotByShipmentIdCache,
				driverByLicenseCache,
				allDriverCache,
				driverSnapshotByShipmentIdCache,
				customerByVatNumberCache,
				customerByCompanyNameCache,
				allCustomerCache,
				customerSnapshotByShipmentIdCache,
				shipmentRouteByRouteUUIDCache,
				shipmentRouteByShipmentCache
			)
		);
		return cacheManager;
	}
	
	/**
	 * Builder interno per l'istanziazione standardizzata delle regioni Caffeine.
	 * <p>
	 * <b>Observability & Monitoring:</b><br>
	 * Il metodo invoca esplicitamente {@code recordStats()}. Questa configurazione è vitale 
	 * per le architetture Enterprise, in quanto espone a JMX e a Spring Boot Actuator le metriche 
	 * di utilizzo (Hit Rate, Miss Rate, Eviction Count), permettendo il monitoraggio in tempo reale 
	 * tramite sistemi come Prometheus o Grafana.
	 * </p>
	 * @param name il nome identificativo della cache da esporre a Spring.
	 * @param maxSize il limite massimo (Upper Bound) di elementi tollerati in RAM prima dell'espulsione.
	 * @return l'istanza nativa di {@link CaffeineCache} pre-configurata.
	 */
	private CaffeineCache buildCache(String name, int maxSize) {
		return new CaffeineCache(
			name,
			Caffeine
				.newBuilder()
				.maximumSize(maxSize)
				.recordStats()
				.build()
		);
	}
}

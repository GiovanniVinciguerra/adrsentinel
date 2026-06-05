package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorTunnelRestriction;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorDistance;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorETA;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorGeometry;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorLatitude;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentroute.ValidatorLongitude;

/**
 * Data Transfer Object (DTO) di input (Request Payload) utilizzato dal Presentation Layer 
 * per acquisire e validare i dati necessari all'aggiornamento di un singolo segmento di rotta (Leg).
 * <p>
 * <b>Contesto Architetturale (Update Strategy):</b><br>
 * Questo record è progettato per gestire le richieste di mutazione (es. tramite chiamate HTTP PUT o PATCH) 
 * dirette a una specifica tratta logistica già esistente nel database. Trasporta in modo sicuro e 
 * immutabile le metriche spaziali e temporali ricalcolate, disaccoppiando totalmente il formato 
 * di scambio della rete (JSON) dall'entità di dominio JPA sottostante.
 * </p>
 * <p>
 * <b>Strategia di Validazione (Strict Custom Validation & Domain-Driven Design):</b><br>
 * Il payload rifiuta l'uso generico di annotazioni standard (come {@code @Min}, {@code @Max} o {@code @NotBlank}) 
 * in favore di un set di validatori custom specializzati (es. {@code @ValidatorLatitude}, {@code @ValidatorTunnelRestriction}). 
 * Questo approccio architetturale:
 * <ul>
 * <li><b>Centralizzazione:</b> Isola e accentra le regole fisiche e normative (es. i limiti spaziali WGS 84, 
 * i codici ammessi dal trattato ADR) all'interno dei rispettivi validatori.</li>
 * <li><b>Fail-Fast:</b> Assicura che qualsiasi payload JSON malformato, malevolo o fisicamente incoerente 
 * venga intercettato e respinto dal framework (con un 400 Bad Request) prima ancora di allocare risorse 
 * nel Controller o nel Service Layer.</li>
 * </ul>
 * </p>
 * @param originLat La latitudine del punto di partenza del segmento, espressa in gradi decimali (sistema WGS 84). 
 * Validata rigorosamente dal motore per rientrare nel range [-90.0, +90.0].
 * @param originLng La longitudine del punto di partenza del segmento, espressa in gradi decimali (sistema WGS 84). 
 * Validata rigorosamente dal motore per rientrare nel range [-180.0, +180.0].
 * @param destLat La latitudine del punto di arrivo di questo specifico segmento (WGS 84).
 * @param destLng La longitudine del punto di arrivo di questo specifico segmento (WGS 84).
 * @param distancekm La distanza stradale ricalcolata della tratta, espressa in chilometri. 
 * Il validatore ne assicura la presenza e la congruità fisica (es. valore strettamente positivo).
 * @param etaMins Il nuovo tempo di percorrenza stimato (ETA) espresso in minuti, basato sui vincoli di traffico 
 * e sulle limitazioni di velocità per i veicoli pesanti (HGV).
 * @param tunnelRestriction Il codice testuale della restrizione gallerie ADR (es. "D_E", "NONE"). 
 * Il validatore assicura che la stringa fornita faccia parte del set normativo legalmente riconosciuto.
 * @param geometry La stringa vettoriale compressa (Encoded Polyline) fornita dal motore cartografico, necessaria per 
 * il rendering grafico della nuova rotta sui client frontend (es. Leaflet/Google Maps).
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentRouteUpdateDTO(@ValidatorLatitude Double originLat, @ValidatorLongitude Double originLng, @ValidatorLatitude Double destLat,
	@ValidatorLongitude Double destLng, @ValidatorDistance Float distancekm, @ValidatorETA Integer etaMins, @ValidatorTunnelRestriction String tunnelRestriction,
	@ValidatorGeometry String geometry) {}

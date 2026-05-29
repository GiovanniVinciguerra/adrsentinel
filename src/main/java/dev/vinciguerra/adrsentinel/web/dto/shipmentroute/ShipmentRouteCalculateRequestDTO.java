package dev.vinciguerra.adrsentinel.web.dto.shipmentroute;

import java.util.List;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberName;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;
import dev.vinciguerra.adrsentinel.web.annotation.shipment.ValidatorAddress;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;
import jakarta.validation.constraints.NotEmpty;

/**
 * Data Transfer Object (DTO) di tipo <i>Stateless</i> utilizzato dal livello di Presentation (Controller REST) 
 * per catturare la richiesta di calcolo di una rotta logistica (Fase di Anteprima/Draft).
 * <p>
 * <b>Contesto Architetturale (Calculate-and-Confirm Pattern):</b><br>
 * Poiché il sistema opera in modalità transazionale differita, al momento della chiamata a questo payload 
 * l'entità {@code Shipment} non esiste ancora nel database. Di conseguenza, il client deve fornire al backend 
 * tutte le "coordinate di business" essenziali per permettere al Service Layer di orchestrare il calcolo con il 
 * motore cartografico esterno (es. OpenRouteService).
 * </p>
 * <p>
 * Il DTO incapsula tre domini informativi fondamentali:
 * <ul>
 * <li><b>Dominio Spaziale:</b> Indirizzi di origine e destinazione per il Geocoding.</li>
 * <li><b>Dominio Fisico:</b> La targa del veicolo, necessaria per dedurre sagoma e limiti di peso dal DB.</li>
 * <li><b>Dominio Normativo:</b> La lista delle merci pericolose (ADR), necessaria per determinare restrizioni su tunnel e percorsi.</li>
 * </ul>
 * </p>
 * @param originAddress L'indirizzo testuale di partenza. Il vincolo custom {@code @ValidatorAddress} garantisce 
 * l'assenza di caratteri malevoli (XSS) e una formattazione base prima dell'invio al Geocoder.
 * @param destinationAddress L'indirizzo testuale di arrivo. Sottoposto ai medesimi controlli sanitari dell'origine.
 * @param licensePlate La Business Key del mezzo di trasporto (Targa). Il vincolo {@code @ValidatorLicensePlate} 
 * ne verifica il formato legale. Consente al backend di recuperare i vincoli strutturali (HGV).
 * @param onuNumbers Collezione di selettori per identificare le merci pericolose a bordo. Il framework JSR-380 
 * impedisce che venga inviato un array vuoto ({@code @NotEmpty}), poiché una spedizione 
 * AdrSentinel priva di merci perderebbe di significato nel dominio applicativo.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentRouteCalculateRequestDTO(@ValidatorAddress String originAddress, @ValidatorAddress String destinationAddress,
		@ValidatorLicensePlate String licensePlate,
		@NotEmpty(message = "Malformed paylod: Invalid argument. The list of onu numbers cannot be empty") List<OnuNumberSelector> onuNumbers) {
	/**
	 * Value Object annidato che funge da selettore naturale (Natural Key Locator) per l'identificazione 
	 * univoca di una merce pericolosa all'interno dell'anagrafica del database.
	 * <p>
	 * <b>Zero-Trust e Information Hiding:</b><br>
	 * Il design espone al client esclusivamente i dati di dominio (Codice ONU, Gruppo Imballaggio e Nome Tecnico) 
	 * nascondendo totalmente l'ID surrogato del database. Questa triade costituisce la chiave composita 
	 * chimico-normativa che il backend utilizzerà per estrarre in sicurezza la corretta {@code AdrClass} e 
	 * il {@code TunnelRestriction Code}.
	 * </p>
	 * @param onuCode Il codice numerico internazionale a 4 cifre (UN Number). Validato da {@code @ValidatorOnuNumberCode}.
	 * @param packingGroup Il livello di pericolosità (Packing Group: I, II, III). Validato da {@code @ValidatorPackingGroup}.
	 * @param name La denominazione tecnica ufficiale (Proper Shipping Name) della sostanza. Validata da {@code @ValidatorOnuNumberName}.
	 * @author Giovanni Vinciguerra
	 * @version 1.0 (Strict Validated Input Payload)
	 * @since 1.0
	 */
	public record OnuNumberSelector(@ValidatorOnuNumberCode String onuCode, @ValidatorPackingGroup String packingGroup, @ValidatorOnuNumberName String name) {}
}

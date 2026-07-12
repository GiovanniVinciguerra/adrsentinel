package dev.vinciguerra.adrsentinel.web.dto.shipmentitem;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorUUID;
import dev.vinciguerra.adrsentinel.web.annotation.dispatch.ValidatorNetWeight;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberCode;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorOnuNumberName;
import dev.vinciguerra.adrsentinel.web.annotation.onunumber.ValidatorPackingGroup;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorOnuPackingCode;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageCount;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageType;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageWeight;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorQuantity;
import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorUnitOfMeasure;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) progettato per la creazione 
 * e l'inserimento di una nuova riga di carico (Shipment Item) nel sistema.
 * <p><b>Contesto Architetturale (Flat Payload & Business Keys):</b></p>
 * Questo record rappresenta lo stato dell'arte per la progettazione di API RESTful disaccoppiate. 
 * Evita l'<i>Anti-Pattern</i> dell'annidamento di oggetti complessi (Over-fetching) e non espone 
 * mai vulnerabili ID sequenziali del database (es. {@code shipment_id = 5}). Utilizza invece 
 * esclusivamente "Chiavi di Business" pubbliche e sicure: il Tracking Number per agganciare 
 * l'articolo alla spedizione padre, e la chiave composita (Codice ONU + Gruppo di Imballaggio) 
 * per recuperare l'esatta direttiva normativa ADR.
 * <p><b>Motore di Validazione Perimetrale (Unified Edge Validation):</b></p>
 * Il payload è blindato da un'architettura di validazione ibrida che agisce come scudo (Firewall Applicativo) 
 * prima dell'accesso al Service Layer:
 * <ul>
 * <li><b>Dominio Logistico ({@code @ValidatorUUID}, {@code @ValidatorOnuNumberCode}):</b> Garantisce 
 * che gli identificatori di sistema e le anagrafiche ONU rispettino i rigidi standard crittografici 
 * (formato UUID) e normativi (esattamente 4 cifre).</li>
 * <li><b>Grandezze Fisiche e Testi ({@code @ValidatorRequiredNumber}, {@code @ValidatorRequiredString}):</b> 
 * Assicura che le quantità siano reali (strettamente positive) e che i metadati testuali (Unità di 
 * misura, Gruppi di imballaggio) non causino <i>Payload Bloating</i> o <i>Data Truncation</i> nel database.</li>
 * </ul>
 * @param quantity La quantità fisica della materia trasportata (rigorosamente > 0 e vincolata alla portata del mezzo).
 * @param netWeightkg La quantità fisica espressa in kilogrammi (rigorosamente maggiore di 0).
 * @param unitOfMeasure L'unità di misura associata alla quantità netta o al volume (es. "KG", "L").
 * @param packageCount La quantità fisica totale dei colli (max 9999). Intercetta errori di battitura macroscopici e 
 * previene buffer overflow.
 * @param packageType La tipologia di contenitore fisico utilizzato (es. DRUM, IBC, TANK). Validata in ingresso per 
 * garantire il <i>Fail-Safe Enum Parsing</i> a protezione del dizionario logistico.
 * @param onuPackingCode Il codice o la lista di codici di omologazione dell'imballaggio (es. "4G", "31HA1"). Sottoposto 
 * a validazione sintattica Lookahead per garantire la conformità alla grammatica ADR.
 * @param packingWeight Il peso della tara dell'imballaggio espresso in chilogrammi (kg). Vincolato a un massimo 
 * strutturale (es. 500.0 kg) per impedire l'inserimento accidentale del peso lordo al posto della tara.
 * @param shipmentTrackingNumber La Business Key primaria (in formato UUID) della spedizione "padre" a cui questo articolo 
 * appartiene.
 * @param onuNumberCode Il codice ONU a 4 cifre esatte che identifica la sostanza pericolosa (prima parte della chiave di 
 * lookup normativo ADR).
 * @param packingGroup Il grado di pericolo o gruppo di imballaggio (es. "I", "II", "III", oppure "VUOTO"), seconda parte 
 * della chiave di lookup ADR.
 * @param name La denominazione ufficiale di trasporto della materia ADR, utilizzata come terza parte per la validazione e 
 * il lookup incrociato.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record ShipmentItemRequestDTO(@ValidatorQuantity Integer quantity, @ValidatorNetWeight Integer netWeightkg, @ValidatorUnitOfMeasure String unitOfMeasure,
	@ValidatorPackageCount Integer packageCount, @ValidatorPackageType String packageType, @ValidatorOnuPackingCode String onuPackingCode,
	@ValidatorPackageWeight Float packingWeight, @ValidatorUUID String shipmentTrackingNumber, @ValidatorOnuNumberCode String onuNumberCode,
	@ValidatorPackingGroup String packingGroup, @ValidatorOnuNumberName String name) {}

package dev.vinciguerra.adrsentinel.web.dto.vehicle;

import dev.vinciguerra.adrsentinel.web.annotation.ValidatorRequiredNumber;
import dev.vinciguerra.adrsentinel.web.annotation.ValidatorRequiredString;
import dev.vinciguerra.adrsentinel.web.annotation.vehicle.ValidatorLicensePlate;

/**
 * Data Transfer Object (DTO) in ingresso (Request Payload) progettato per il censimento 
 * e l'immatricolazione di un nuovo veicolo all'interno della flotta aziendale.
 * <p><b>Contesto Architetturale (Scudo di Validazione Perimetrale):</b></p>
 * Questo record agisce come un vero e proprio "Firewall Applicativo". Sfruttando le 
 * macro-annotazioni unificate, blocca le richieste HTTP malformate o malevole prima 
 * ancora che vengano allocate risorse nel Service Layer:
 * <ul>
 * <li><b>Domain Shield ({@code @ValidatorLicensePlate}):</b> Garantisce che la targa rispetti 
 * rigorosamente i pattern legali, prevenendo l'inquinamento dell'anagrafica con dati non strutturati.</li>
 * <li><b>Type Flattening ({@code @ValidatorRequiredString}):</b> Accetta i tipi di veicolo e di carico 
 * come stringhe piatte, disaccoppiando il contratto API dalle implementazioni interne degli Enum.</li>
 * <li><b>Grandezze Fisiche ({@code @ValidatorRequiredNumber}):</b> Assicura che le metriche strutturali 
 * e telemetriche (pesi, dimensioni, assi) siano presenti, numeriche e strettamente positive.</li>
 * </ul>
 * <p><b>Design Auto-Documentante (Unit Suffixes):</b></p>
 * L'adozione del pattern "Unit Suffix" nei nomi delle variabili (es. {@code kg}, {@code m}) elimina 
 * ogni ambiguità di misurazione tra client e server. Questa prassi previene errori catastrofici 
 * negli algoritmi di Load Balancing e Routing spaziale (es. confondere centimetri con metri).
 * @param licensePlate La targa del veicolo, utilizzata come Business Key primaria e identificatore visivo.
 * @param vehicleType La classificazione tecnica ADR del mezzo richiesta (es. "FL", "AT", "EX/II").
 * @param loadType La tipologia di allestimento per il carico (es. "TANK", "BULK").
 * @param maxWeightkg La massa massima ammissibile a pieno carico, rigorosamente in chilogrammi.
 * @param maxUsefulWeightkg La portata utile effettiva del mezzo, vitale per il calcolo del carico residuo (in chilogrammi).
 * @param heightm L'altezza massima del veicolo (in metri), parametro critico per i vincoli infrastrutturali.
 * @param widthm La larghezza massima del veicolo (in metri).
 * @param lengthm La lunghezza totale del veicolo (in metri).
 * @param wheelbasem Il passo del veicolo (distanza tra gli assi in metri).
 * @param nAxles Il numero totale di assi, necessario per la distribuzione del peso e i calcoli di pedaggio.
 * @param adrCertified Flag primitivo che indica l'idoneità al trasporto ADR. Sfrutta l'assenza della classe Wrapper 
 * per garantire un fallback nativo a {@code false} (Secure by Default) in caso di payload JSON incompleto.
 * @author Giovanni Vinciguerra
 * @version 1.0 (Strict Validated Input Payload)
 * @since 1.0
 */
public record VehicleRequestDTO(@ValidatorLicensePlate String licensePlate, @ValidatorRequiredString String vehicleType,
	@ValidatorRequiredString String loadType, @ValidatorRequiredNumber Integer maxWeightkg,
	@ValidatorRequiredNumber Integer maxUsefulWeightkg, @ValidatorRequiredNumber Float heightm,
	@ValidatorRequiredNumber Float widthm, @ValidatorRequiredNumber Float lengthm,
	@ValidatorRequiredNumber Float wheelbasem, @ValidatorRequiredNumber Integer nAxles, boolean adrCertified) {}

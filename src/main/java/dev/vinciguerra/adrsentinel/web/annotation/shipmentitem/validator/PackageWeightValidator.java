package dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.validator;

import dev.vinciguerra.adrsentinel.web.annotation.shipmentitem.ValidatorPackageWeight;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Implementazione concreta del validatore custom legato all'annotazione {@link ValidatorPackageWeight}.
 * <p><b>Contesto Architetturale (Aritmetica Sicura &amp; Anti-Corruption):</b></p>
 * Questa classe opera allo strato di ingresso del payload (DTO) per validare la consistenza 
 * del peso associato alla tara dell'imballaggio (package weight). Oltre a imporre vincoli di 
 * business logistici, funge da presidio di sicurezza algoritmica, isolando lo strato di 
 * persistenza e i motori di calcolo GIS/routing da valori numerici fluttuanti o malformati.
 * <p><b>Logica di Dominio (Soglie di Sicurezza ADR):</b></p>
 * Il range di validazione [0.0, 500.0] è strettamente legato alle specifiche costruttive 
 * dei contenitori omologati dalle Nazioni Unite (ONU):
 * <ul>
 * <li>Il limite inferiore consente il valore {@code 0.0f} solo ed esclusivamente per supportare 
 * a monte le merci non imballate (es. cisterne o rinfusa), le cui logiche di interdipendenza 
 * incrociata sono demandate ai validatori di entità.</li>
 * <li>Il limite superiore (500.0 kg) rispecchia la capacità strutturale massima di tara 
 * per i grandi imballaggi metallici o IBC rinforzati, agendo da barriera contro errori macroscopici 
 * di digitazione (es. inserimento del peso lordo del pallet nel campo della sola tara).</li>
 * </ul>
 * @author Giovanni Vinciguerra
 * @version 1.0
 * @since 3.0
 * @see ValidatorPackageWeight
 */
public class PackageWeightValidator implements ConstraintValidator<ValidatorPackageWeight, Float> {
	/**
	 * Esegue la validazione formale e matematica sul peso della tara in ingresso.
	 * <p><b>Pipeline di Validazione Matematica (Fail-Fast):</b></p>
	 * <ol>
	 * <li><i>Null-Safety:</i> Rifiuta immediatamente valori nulli, garantendo che l'attributo 
	 * sia valorizzato durante le operazioni di stesura del D.D.T.</li>
	 * <li><i>IEEE 754 Floating-Point Protection:</i> Verifica che il numero non sia infinito 
	 * ({@link Float#isInfinite()}) o non definito ({@link Float#isNaN()}). Questo controllo previene 
	 * vulnerabilità di tipo "NaN Propagation", che potrebbero corrompere i calcoli aggregati 
	 * della massa totale del veicolo mandando in crash i motori di calcolo o provocando anomalie di runtime.</li>
	 * <li><i>Range Check:</i> Verifica che il valore sia compreso tra 0.0f e 500.0f chilogrammi.</li>
	 * </ol>
	 * @param value Il valore float rappresentante il peso in kg da sottoporre a verifica.
	 * @param context Il contesto strutturale fornito dal motore di validazione Jakarta.
	 * @return {@code true} se il valore rispetta l'integrità aritmetica e i vincoli fisici ADR, {@code false} altrimenti.
	 */
	@Override
	public boolean isValid(Float value, ConstraintValidatorContext context) {
		if(value == null)
			return false;
		if(value.isInfinite() || value.isNaN())
			return false;
		return value >= 0.0f && value <= 500.0f;
	}
}

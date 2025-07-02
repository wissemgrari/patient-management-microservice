package com.pm.patientservice.controller;

import com.pm.patientservice.dto.PatientRequestDTO;
import com.pm.patientservice.dto.PatientResponseDTO;
import com.pm.patientservice.dto.validator.CreatePatientValidationGroup;
import com.pm.patientservice.service.PatientService;
import jakarta.validation.groups.Default;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patients")
public class PatientController {
  private final PatientService patientService;
  
  public PatientController(PatientService patientService) {
    this.patientService = patientService;
  }
  
  @GetMapping
  public ResponseEntity<List<PatientResponseDTO>> findAll() {
    List<PatientResponseDTO> patients = patientService.getPatients();
    return ResponseEntity.ok().body(patients);
  }
  
  @PostMapping
  public ResponseEntity<PatientResponseDTO> createPatient(
    @Validated({Default.class, CreatePatientValidationGroup.class}) @RequestBody PatientRequestDTO patientRequestDTO) {
    PatientResponseDTO patientResponseDTO = patientService.createPatient(patientRequestDTO);
    return ResponseEntity.ok().body(patientResponseDTO);
  }
  
  @PutMapping("/{id}")
  public ResponseEntity<PatientResponseDTO> updatePatient(@PathVariable("id") UUID id,
                                                          @Validated({Default.class}) @RequestBody
                                                          PatientRequestDTO patientRequestDTO) {
    PatientResponseDTO patientResponseDTO = patientService.updatePatient(id, patientRequestDTO);
    return ResponseEntity.status(HttpStatus.CREATED).body(patientResponseDTO);
  }
  
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deletePatient(@PathVariable("id") UUID id) {
    patientService.deletePatient(id);
    return ResponseEntity.noContent().build();
  }
}

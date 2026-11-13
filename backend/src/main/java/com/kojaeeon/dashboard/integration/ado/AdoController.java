package com.kojaeeon.dashboard.integration.ado;

import com.kojaeeon.dashboard.shared.ApiErrors.Error;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ado")
public class AdoController {

  private final AdoService service;

  public AdoController(AdoService service) {
    this.service = service;
  }

  public record Query(String query) {}

  @PostMapping("/wiql")
  public AdoService.Result query(@RequestBody Query input) {
    return service.query(input.query());
  }

  @ExceptionHandler(AdoService.AdoException.class)
  ResponseEntity<Error> error(AdoService.AdoException e) {
    return ResponseEntity.status(502).body(new Error(e.getMessage()));
  }
}

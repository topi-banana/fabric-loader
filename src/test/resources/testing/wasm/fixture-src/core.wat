(module
  (memory (export "memory") 1)
  (func (export "init"))
  (func (export "on-hook") (param i32) (result i32)
    i32.const 0)
  (func (export "cabi_realloc") (param i32 i32 i32 i32) (result i32)
    i32.const 0)
)

(module
  (memory (export "memory") 1 4)
  (func (export "init"))
  ;; exercises memory.copy and memory.fill, which the interpreter may route through helpers
  (func (export "bulk") (result i32)
    i32.const 0    i32.const 0x41  i32.const 64   memory.fill
    i32.const 128  i32.const 0     i32.const 64   memory.copy
    i32.const 128  i32.load8_u)
  ;; exercises memory growth, which goes through the allocation strategy
  (func (export "grow") (result i32)
    i32.const 2 memory.grow)
)

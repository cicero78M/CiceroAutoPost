const express = require('express');
const bodyParser = require('body-parser');
const app = express();
app.use(bodyParser.json());

const routineTasks = [];
const specialTasks = [];
let routineId = 1;
let specialId = 1;

app.get('/api/routine-tasks', (req, res) => {
  res.json({ data: routineTasks });
});

app.post('/api/routine-tasks', (req, res) => {
  const task = { id: routineId++, ...req.body };
  routineTasks.push(task);
  res.status(201).json({ data: task });
});

app.put('/api/routine-tasks/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = routineTasks.findIndex(t => t.id === id);
  if (idx === -1) return res.sendStatus(404);
  routineTasks[idx] = { id, ...req.body };
  res.json({ data: routineTasks[idx] });
});

app.delete('/api/routine-tasks/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = routineTasks.findIndex(t => t.id === id);
  if (idx === -1) return res.sendStatus(404);
  routineTasks.splice(idx, 1);
  res.sendStatus(204);
});

app.get('/api/amplifikasi-tugas-khusus', (req, res) => {
  res.json({ data: specialTasks });
});

app.post('/api/amplifikasi-tugas-khusus', (req, res) => {
  const task = { id: specialId++, ...req.body };
  specialTasks.push(task);
  res.status(201).json({ data: task });
});

app.put('/api/amplifikasi-tugas-khusus/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = specialTasks.findIndex(t => t.id === id);
  if (idx === -1) return res.sendStatus(404);
  specialTasks[idx] = { id, ...req.body };
  res.json({ data: specialTasks[idx] });
});

app.delete('/api/amplifikasi-tugas-khusus/:id', (req, res) => {
  const id = parseInt(req.params.id, 10);
  const idx = specialTasks.findIndex(t => t.id === id);
  if (idx === -1) return res.sendStatus(404);
  specialTasks.splice(idx, 1);
  res.sendStatus(204);
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`API server running on port ${PORT}`));
